package local.sise.ores.probe;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.nio.file.*;
import java.util.*;
import local.sise.ores.*;
import local.sise.compat.materials.*;
import local.sise.compat.materials.RecipeSafety;
import local.sise.compat.RecipePatches;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod("sise_ores_probe")
public final class OresProbe {
    private static boolean started, finished;
    private static int ticks, worldTicks;
    private static volatile int stage;
    private static Path directory;
    private static final List<String> checks = new ArrayList<>();
    public OresProbe() {
        if (Boolean.getBoolean("sise.ores.verify")) {
            NeoForge.EVENT_BUS.addListener(OresProbe::tick);
            RecipeAudit.register();
        }
    }
    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message); checks.add(message);
    }
    private static Item item(String id) { return BuiltInRegistries.ITEM.getValue(Identifier.parse(id)); }
    private static Recipe<?> recipe(MinecraftServer server, String id) {
        return server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse(id))).orElseThrow(() -> new IllegalStateException("Missing recipe " + id)).value();
    }
    private static JsonObject encode(MinecraftServer server, Recipe<?> recipe) {
        return Recipe.CODEC.encodeStart(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), recipe).getOrThrow().getAsJsonObject();
    }
    private static void verify(MinecraftServer server) {
        checks.addAll(CompatChecks.verify(server));
        checks.addAll(AllMaterialChecks.verify(server));
        checks.addAll(PartMaterialChecks.verify(server));
        checks.addAll(FluidChecks.verify(server));
        checks.addAll(ProductionChecks.verify(server));
        checks.addAll(EquipmentChecks.verify(server));
        var raritySource = new java.util.concurrent.ConcurrentHashMap<Identifier, Integer>();
        raritySource.put(SiseOres.id("tin_ingot"), 1);
        var rarityPacket = new org.yanbwe.raritycore.network.RaritySyncPayload(raritySource, raritySource, List.of());
        raritySource.clear();
        check(rarityPacket.itemRarityData().size() == 1 && rarityPacket.autoRarityData().size() == 1, "rarity sync uses immutable snapshots");
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            org.yanbwe.raritycore.network.RaritySyncPayload.STREAM_CODEC.encode(buffer, rarityPacket);
            var decoded = org.yanbwe.raritycore.network.RaritySyncPayload.STREAM_CODEC.decode(buffer);
            check(decoded.equals(rarityPacket) && buffer.readableBytes() == 0, "rarity sync round trip consumes exactly one complete packet");
        } finally { buffer.release(); }
        check(ModList.get().getModContainerById("sise_ores").orElseThrow().getModInfo().getDisplayName().equals("SISE Ores"), "real source mod is SISE Ores");
        check(((Number)RecipeUnifier.lastReport.getOrDefault("changed_recipes", 0)).intValue() >= 550, "recipe event ran in full modpack");
        check(((Map<?, ?>)RecipeUnifier.lastReport.get("unresolved")).isEmpty(), "no unresolved legacy material references");
        for (var material : MaterialCatalog.MATERIALS) {
            var target = item(material.target());
            check(target != null && target != Items.AIR, "registered " + material.target());
            var tag = TagKey.create(Registries.ITEM, Identifier.parse(material.tag()));
            check(new ItemStack(target).is(tag), "common tag " + material.tag());
            var equivalents = TagKey.create(Registries.ITEM, Identifier.parse(material.inputTag().substring(1)));
            check(new ItemStack(target).is(equivalents), "target in exact equivalence group " + material.name());
            for (var source : material.members()) {
                check(item(source) != null && item(source) != Items.AIR, "legacy item exists " + source);
                if(material.form().equals("equipment")){
                    check(server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,Identifier.parse("sise_ores:convert/"+material.name()+"/"+source.replace(':','/')))).isEmpty(),"equipment unifies original crafting directly without a stonecutter conversion "+source);
                    continue;
                }
                var input = new SingleRecipeInput(new ItemStack(item(source)));
                var standardInput = new SingleRecipeInput(new ItemStack(target));
                var conversion = (StonecutterRecipe)recipe(server, "sise_ores:convert/" + material.name() + "/" + source.replace(':', '/'));
                check(conversion.matches(input, server.overworld()), "conversion accepts legacy " + source);
                check(!conversion.matches(standardInput, server.overworld()), "conversion excludes self " + source);
                var result = conversion.assemble(input);
                check(result.is(target) && result.getCount() == 1, "conversion preserves one item " + source);
                for (int count : List.of(1, 7, 64)) {
                    var drop = MaterialLootModifier.normalize(new ItemStack(item(source), count));
                    check(drop.is(target) && drop.getCount() == count, "loot normalization preserves yield " + source + " " + count);
                }
                var named = new ItemStack(item(source));
                named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Special material"));
                check(MaterialLootModifier.normalize(named) == named, "component-bearing legacy stack preserved " + source);
            }
            if (material.form().equals("storage_blocks")) {
                var block = SiseOres.MATERIAL_BLOCKS.get(material.name()).get();
                var pos = new BlockPos(10, 80, 10);
                server.overworld().setBlock(pos, block.defaultBlockState(), 3);
                var drops = Block.getDrops(block.defaultBlockState(), server.overworld(), pos, null);
                check(drops.size() == 1 && drops.getFirst().is(target) && drops.getFirst().getCount() == 1, "placed block drops itself " + material.name());
                server.overworld().removeBlock(pos, false);
            }
        }
        var changed = (Map<?, ?>)RecipeUnifier.lastReport.get("recipes");
        int loaded = 0;
        for (var name : changed.keySet()) {
            var holder = server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse(name.toString())));
            if (holder.isEmpty()) continue; // Conditional recipes may intentionally be disabled.
            loaded++;
            check(RecipeTransform.remainingSources(encode(server, holder.get().value())).isEmpty(), "loaded recipe unified " + name);
        }
        check(loaded >= 500, "at least 500 rewritten recipes actually deserialized");
        check(loaded == changed.size(), "every runtime-unified recipe actually loaded");
        check(RecipePatches.appliedInCategory("legacy_material_format") == 8, "eight legacy material recipes repaired by SISE Compat");
        check(RecipePatches.appliedInCategory("mineral_format") == 7, "seven additional mineral processing repairs owned by SISE Compat");
        for (var id : MineralRecipes.CRUSHING) {
            var r = (mekanism.api.recipes.ItemStackToItemStackRecipe)recipe(server, id);
            var in = r.getInput().ingredient();
            var raw = in.ingredient().items().findFirst().orElseThrow().value();
            var sample = new SingleRecipeInput(new ItemStack(raw, in.count()));
            check(r.matches(sample, server.overworld()) && !r.assemble(sample).isEmpty(), "restored mineral crushing executes " + id);
        }
        for (var id : MineralRecipes.CRYSTAL_FIXING) {
            var encoded = encode(server, recipe(server, id));
            check(encoded.getAsJsonObject("fuel").get("ingredient").getAsString().equals("#c:gems/energized_crystal"), "restored crystal fuel tag " + id);
            check(encoded.get("chance").getAsInt() == (id.contains("flawed_") ? 500 : 8000), "crystal repair chance unchanged " + id);
        }
        for (var id : List.of("enderio:sag_milling/sand", "enderio:sag_milling/clay", "enderio:sag_milling/redstone_ore")) {
            var sag = (com.enderio.enderio.content.machines.sag_mill.SagMillingRecipe)recipe(server, id);
            check(sag.outputs().stream().anyMatch(o -> o.getItemStackTemplate().map(t -> t.create().is(item("sise_ores:silicon"))).orElse(false)), "SAG resolves silicon output to SISE " + id);
        }
        var siliconUnpacker = (aztech.modern_industrialization.machines.recipe.MachineRecipe)recipe(server, "modern_industrialization:materials/silicon/unpacker/ingot");
        for (var id : List.of("sise_ores:silicon_block", "oritech:silicon_block", "extendedae:silicon_block", "sise_ores:silicon_metal_block", "modern_industrialization:silicon_block")) {
            var slot = new aztech.modern_industrialization.inventory.ConfigurableItemStack();
            slot.setKey(net.neoforged.neoforge.transfer.item.ItemResource.of(item(id))); slot.setAmount(1);
            boolean matched = aztech.modern_industrialization.machines.components.CrafterComponent.doInputsMatch(List.of(slot), List.of(), siliconUnpacker);
            check(matched == (id.equals("sise_ores:silicon_metal_block") || id.equals("modern_industrialization:silicon_block")), "MI silicon unpacking separates families " + id);
        }
        for (var material : MaterialCatalog.MATERIALS) if (material.form().equals("raw_materials") && !material.material().equals("silicon")) {
            var oreTag = TagKey.create(Registries.BLOCK, Identifier.parse("c:ores/" + material.material()));
            int ores = 0;
            for (var holder : BuiltInRegistries.BLOCK.getTagOrEmpty(oreTag)) {
                var block = holder.value();
                var drops = Block.getDrops(block.defaultBlockState(), server.overworld(), new BlockPos(10, 80, 10), null);
                check(drops.stream().noneMatch(s -> MaterialCatalog.BY_SOURCE.containsKey(BuiltInRegistries.ITEM.getKey(s.getItem()).toString())), "ore loot has no old material " + BuiltInRegistries.BLOCK.getKey(block));
                if (drops.stream().anyMatch(s -> s.is(item(material.target())))) ores++;
            }
            check(ores > 0, "real ore loot produces unified raw metal " + material.name());
        }
        for (var material : List.of("aluminum", "tungsten")) {
            check(encode(server, recipe(server, "neoecoae:crushing/" + material + "_dust")).toString().contains("sise_ores:" + material + "_dust"), "NeoEcoAE crushing loads " + material);
            for (var form : List.of("ore", "raw", "raw_block"))
                check(encode(server, recipe(server, "neoecoae:enriching/" + material + "_dust_from_" + form)).toString().contains("sise_ores:" + material + "_dust"), "NeoEcoAE enriching loads " + material + "/" + form);
        }
        for (var id : List.of("mekanism:processing/iron/dust/from_ingot", "enderio:sag_milling/iron", "modern_industrialization:materials/iron/forge_hammer/ingot_to_dust"))
            check(encode(server, recipe(server, id)).toString().contains("sise_ores:iron_dust"), "cross-mod iron dust output " + id);
        check(encode(server, recipe(server, "oritech:foundry/alloy/steel")).toString().contains("sise_ores:steel_ingot"), "Oritech steel output");
        check(encode(server, recipe(server, "oritech:pulverizer/biosteel")).toString().contains("oritech:biosteel_dust"), "biosteel protected");
        check(!new ItemStack(item("oritech:biosteel_ingot")).is(TagKey.create(Registries.ITEM, SiseOres.id("equivalents/ingots/steel"))), "biosteel outside exact steel group");
        var unpack = (CraftingRecipe)recipe(server, "mekanism:processing/tin/ingot/from_block");
        for (String block : List.of("sise_ores:tin_block", "mekanism:block_tin", "modern_industrialization:tin_block")) {
            var input = CraftingInput.of(1, 1, List.of(new ItemStack(item(block))));
            check(unpack.matches(input, server.overworld()), "unpack accepts " + block);
            var output = unpack.assemble(input);
            check(output.is(item("sise_ores:tin_ingot")) && output.getCount() == 9, "nine ingots preserved " + block);
        }
        var pack = (CraftingRecipe)recipe(server, "mekanism:processing/tin/ingot/from_nuggets");
        var nuggets = new ArrayList<ItemStack>();
        for (int i = 0; i < 9; i++) nuggets.add(new ItemStack(item(i % 2 == 0 ? "sise_ores:tin_nugget" : "modern_industrialization:tin_nugget")));
        var input = CraftingInput.of(3, 3, nuggets);
        check(pack.matches(input, server.overworld()), "mixed nuggets accepted");
        check(pack.assemble(input).is(item("sise_ores:tin_ingot")) && pack.assemble(input).getCount() == 1, "nine nuggets produce exactly one ingot");
        for (var material : MaterialCatalog.MATERIALS) {
            if (!material.form().equals("storage_blocks")) continue;
            String metal = material.material();
            var pairs = material.name().startsWith("raw_") ? List.of(List.of(material.name(), "raw_" + metal))
                : material.name().equals("silicon_block") ? List.of(List.of("silicon_block", "silicon"))
                : material.name().equals("charcoal_block") ? List.of(List.of("charcoal_block", "minecraft:charcoal"))
                : material.name().equals("salt_block") ? List.of(List.of("salt_block", "salt_dust"))
                : List.of(List.of(material.name(), metal + "_ingot"), List.of(metal + "_ingot", metal + "_nugget"));
            for (var pair : pairs) {
                var large = item("sise_ores:" + pair.get(0));
                var small = item(pair.get(1).contains(":") ? pair.get(1) : "sise_ores:" + pair.get(1));
                var one = CraftingInput.of(1, 1, List.of(new ItemStack(large)));
                var nine = CraftingInput.of(3, 3, java.util.stream.IntStream.range(0, 9).mapToObj(i -> new ItemStack(small)).toList());
                boolean foundPack = false, foundUnpack = false;
                for (var h : server.getRecipeManager().getRecipes()) if (h.value() instanceof CraftingRecipe c) {
                    if (c.matches(one, server.overworld())) {
                        var out = c.assemble(one);
                        check(out.is(small) && out.getCount() == 9, "unambiguous unpack " + metal + " " + pair + " " + h.id().identifier());
                        foundUnpack = true;
                    }
                    if (c.matches(nine, server.overworld())) {
                        var out = c.assemble(nine);
                        check(out.is(large) && out.getCount() == 1, "unambiguous pack " + metal + " " + pair + " " + h.id().identifier());
                        foundPack = true;
                    }
                }
                check(foundPack && foundUnpack, "reversible 9:1 " + metal + " " + pair);
            }
        }
        for (String id : RecipeSafety.DUST_SPLITS) {
            check(recipe(server, id) instanceof CraftingRecipe, "dust subdivision stays on workbench " + id);
        }
        var bio = CraftingInput.of(3, 3, java.util.stream.IntStream.range(0, 9).mapToObj(i -> new ItemStack(item("oritech:biosteel_ingot"))).toList());
        for (String id : List.of("mekanism:storage_blocks/steel", "modern_industrialization:materials/steel/craft/block_from_ingot", "oritech:steelblock", "ic2:shaped/steel_block"))
            check(!((CraftingRecipe)recipe(server, id)).matches(bio, server.overworld()), "ordinary steel packing rejects biosteel " + id);
    }
    private static void tick(ClientTickEvent.Post event) {
        if (finished) return;
        var mc = Minecraft.getInstance();
        try {
            if (!started) {
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
                directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                if (!directory.equals(Path.of(System.getProperty("sise.ores.fixture", "")).toAbsolutePath().normalize()) || !Files.isRegularFile(directory.resolve("SISE_ORES_AUTOTEST_ONLY"))) throw new IllegalStateException("Not an isolated SISE Ores fixture");
                started = true;
                mc.createWorldOpenFlows().createFreshLevel("sise-ores-verification-" + System.currentTimeMillis(), new LevelSettings("SISE Ores verification", GameType.CREATIVE,
                    new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(602, false, false), WorldPresets::createNormalWorldDimensions, mc.screen);
                return;
            }
            if (++ticks > 12000) throw new IllegalStateException("World test timed out");
            if (mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen) throw new IllegalStateException("Disconnected from fixture world; inspect debug.log");
            if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || mc.player.tickCount < 50 || ++worldTicks % 80 != 0) return;
            var server = mc.getSingleplayerServer();
            if (Boolean.getBoolean("sise.ores.inventory")) {
                server.submit(() -> FluidInventory.export(server,directory)).join();
                server.submit(() -> RecipeAudit.export(server,directory)).join();
                checks.add("live fluid, chemical and UU registry inventory exported");
                finish(true, ""); return;
            }
            if (stage == 0) {
                if (local.sise.ores.compat.OresJeiPlugin.runtime == null) return;
                server.submit(() -> verify(server)).join();
                server.submit(() -> WorldgenChecks.verify(server, directory)).join();
                server.submit(() -> RecipeAudit.export(server, directory)).join();
                var jei = local.sise.ores.compat.OresJeiPlugin.runtime;
                checks.addAll(FluidChecks.client());
                checks.addAll(ProductionChecks.client());
                checks.addAll(EquipmentChecks.client());
                check(jei.getJeiHelpers().getModIdHelper().getModNameForModId("sise_ores").equals("SISE Ores"), "JEI reports SISE Ores as source");
                var visibility = jei.getJeiHelpers().getIngredientVisibility();
                check(!visibility.isIngredientVisible(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, new ItemStack(item("mekanism:ingot_tin")), mezz.jei.api.ingredients.subtypes.UidContext.Ingredient), "old tin hidden from JEI browser");
                check(visibility.isIngredientVisible(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, new ItemStack(item("sise_ores:tin_ingot")), mezz.jei.api.ingredients.subtypes.UidContext.Ingredient), "standard tin visible in JEI browser");
                check(visibility.isIngredientVisible(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, new ItemStack(item("oritech:biosteel_ingot")), mezz.jei.api.ingredients.subtypes.UidContext.Ingredient), "biosteel visible in JEI browser");
                for (var m : MaterialCatalog.MATERIALS) {
                    if (!m.owned()) continue;
                    check(mc.getResourceManager().getResource(SiseOres.id("items/" + m.name() + ".json")).isPresent(), "client item model " + m.name());
                    check(!new ItemStack(item(m.target())).getHoverName().getString().startsWith("item.sise_ores."), "localized " + m.name());
                }
                server.submit(() -> {
                    var inventory = new SimpleContainer(54); int i = 0;
                    for (var material : MaterialCatalog.MATERIALS.stream().sorted(java.util.Comparator.comparingInt(m -> Set.of("matter","buckets").contains(m.form())?0:Set.of("plates","gears").contains(m.form())?1:m.form().equals("ores")?2:3)).toList()) { if (i < 54) inventory.setItem(i++, new ItemStack(item(material.target()))); }
                    var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                    player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x6, id, inv, inventory, 6), Component.literal("SISE Ores · " + ModList.get().getModContainerById("sise_ores").orElseThrow().getModInfo().getVersion())));
                }).join();
                stage++;
            } else if (stage == 1) {
                Screenshot.takeScreenshot(mc.getMainRenderTarget(), screenshot -> {
                    try (screenshot) { var p = directory.resolve("screenshots/sise-ores-materials.png"); Files.createDirectories(p.getParent()); screenshot.writeToFile(p); }
                    catch (Exception e) { finish(false, e.toString()); }
                });
                stage++;
                server.submit(() -> server.reloadResources(server.getPackRepository().getSelectedIds())).thenCompose(f -> f)
                    .thenRun(() -> server.execute(() -> {
                        try { verify(server); RecipeAudit.verifyReload(server, directory); stage = 3; } catch (Throwable e) { finish(false, e.toString()); }
                    }));
            } else if (stage == 3) finish(true, "");
        } catch (Throwable e) { e.printStackTrace(); finish(false, e.toString()); }
    }
    private static void finish(boolean passed, String error) {
        if (finished) return; finished = true;
        try { Files.writeString(directory.resolve("sise-ores-probe.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("passed", passed, "assertions", checks.size(), "checks", checks, "error", error))); }
        catch (Exception e) { e.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
}
