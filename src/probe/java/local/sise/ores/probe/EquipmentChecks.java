package local.sise.ores.probe;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import java.nio.file.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import local.sise.ores.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.Enchantments;

/** Native recipe execution proves that equal grids return one equipment identity without extra tools. */
final class EquipmentChecks {
    private static final List<String> checks=new ArrayList<>();
    private static void check(boolean value,String label){if(!value)throw new IllegalStateException(label);checks.add(label);}
    private static Item item(String id){return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));}
    private static Recipe<?> recipe(MinecraftServer server,String id){return server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,Identifier.parse(id))).orElseThrow().value();}
    private static ItemStack sample(JsonElement json){
        var input=ic2ce.neoforge.recipe.codec.RecipeValueCodec.parseInput(json);
        return input.getInputs().stream().filter(s->BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().equals("sise_ores")).findFirst().orElseGet(()->input.getInputs().getFirst()).copyWithCount(input.getAmount());
    }
    static List<String> verify(MinecraftServer server){
        checks.clear();var ops=server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var groups=MaterialCatalog.MATERIALS.stream().filter(m->m.form().equals("equipment")).toList();
        check(groups.size()==14,"all fourteen overlapping-recipe equipment families are unified");
        JsonArray catalog;
        try(var reader=new InputStreamReader(Objects.requireNonNull(MaterialCatalog.class.getResourceAsStream("/sise_ores/materials.json")),StandardCharsets.UTF_8)){
            catalog=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("materials");
        }catch(IOException error){throw new IllegalStateException(error);}
        for(var group:groups){
            boolean overlap=false;
            check(!group.owned() && group.target().equals(group.preferred()),"equipment retains the real priority implementation and native attributes "+group.name());
            var definitions=new ArrayList<String>();
            for(var definition:catalog)if(definition.getAsJsonObject().get("name").getAsString().equals(group.name()))
                for(var rid:definition.getAsJsonObject().getAsJsonArray("equipment_recipes"))definitions.add(rid.getAsString());
            check(definitions.size()>=2,"all original equipment crafting routes are recorded "+group.name());
            for(String rid:definitions){
            var raw=RecipeAudit.before.get(rid).getAsJsonObject();
            check(!local.sise.compat.RecipePatches.RULES.getAsJsonObject("patches").has(rid),"equipment no longer has a layout-changing compatibility patch "+rid);
            var expectedJson=RecipeTransform.transform(raw).json();
            check(raw.get("pattern").equals(expectedJson.get("pattern")) && raw.getAsJsonObject("key").size()==expectedJson.getAsJsonObject("key").size(),"original equipment grid and ingredient slot count remain intact "+rid);
            check(!expectedJson.toString().contains("ic2:forge_hammer"),"original equipment crafting needs no extra forge hammer "+rid);
            var actual=(CraftingRecipe)recipe(server,rid);var expected=Recipe.CODEC.parse(ops,expectedJson).getOrThrow();
            check(Recipe.CODEC.encodeStart(ops,actual).getOrThrow().equals(Recipe.CODEC.encodeStart(ops,expected).getOrThrow()),"native equipment recipe differs only by reviewed identities "+rid);
            var rows=raw.getAsJsonArray("pattern");int width=rows.get(0).getAsString().length(),height=rows.size();var stacks=new ArrayList<ItemStack>();
            for(var row:rows)for(char c:row.getAsString().toCharArray())stacks.add(c==' '?ItemStack.EMPTY:sample(raw.getAsJsonObject("key").get(Character.toString(c))));
            var grid=CraftingInput.of(width,height,stacks);check(actual.matches(grid,server.overworld()),"original grid executes directly "+rid);
            var output=actual.assemble(grid);var preferred=new ItemStack(item(group.target()));
            check(ItemStack.matches(output,preferred),"crafted native attributes and default components match priority equipment "+rid);
            int matching=0;
            for(var holder:server.getRecipeManager().getRecipes())if(holder.value() instanceof CraftingRecipe candidate && candidate.matches(grid,server.overworld())){
                check(ItemStack.matches(preferred,candidate.assemble(grid)),"every matching equipment recipe returns the same native item "+rid+" / "+holder.id().identifier());matching++;
            }
            check(matching>=1,"each original equipment crafting route remains available "+rid);
            overlap|=matching>=2;
            var mirrored=new ArrayList<ItemStack>();for(int y=0;y<height;y++)for(int x=0;x<width;x++)mirrored.add(stacks.get(y*width+width-1-x));
            check(actual.matches(CraftingInput.of(width,height,mirrored),server.overworld()),"restored equipment recipe supports its original mirror "+rid);
            }
            check(overlap,"original equipment recipe domains overlap "+group.name());
            for(var source:group.members()){
                var legacy=new ItemStack(item(source));legacy.setDamageValue(7);legacy.set(DataComponents.CUSTOM_NAME,Component.literal("Preserve equipment data"));
                legacy.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING),2);
                var normalized=MaterialLootModifier.normalize(legacy);
                int expectedDamage=new ItemStack(item(group.target())).isDamageableItem()?7:0;
                check(normalized.is(item(group.target())) && normalized.getDamageValue()==expectedDamage,"equipment loot adopts priority native durability semantics "+source);
                check(normalized.get(DataComponents.CUSTOM_NAME).equals(legacy.get(DataComponents.CUSTOM_NAME)) && normalized.get(DataComponents.ENCHANTMENTS).equals(legacy.get(DataComponents.ENCHANTMENTS)),"equipment loot preserves name and enchantments "+source);
                check(legacy.is(item(source)) && legacy.getDamageValue()==7,"equipment normalization leaves its input untouched "+source);
                check(MaterialLootModifier.normalize(normalized)==normalized,"canonical native equipment does not convert again "+source);
            }
        }
        verifyChanges(server);
        return List.copyOf(checks);
    }
    private static void verifyChanges(MinecraftServer server){
        var fixture=Path.of(System.getProperty("sise.ores.fixture"));
        try{
            var baseline=JsonParser.parseString(Files.readString(fixture.resolve("equipment-baseline.json"))).getAsJsonObject().getAsJsonObject("loaded");
            var restored=new HashSet<String>();
            for(var id:JsonParser.parseString(Files.readString(fixture.resolve("equipment-restored-layouts.json"))).getAsJsonArray())restored.add(id.getAsString());
            var changed=new TreeSet<String>();var identities=new TreeSet<String>();
            var ops=server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            var loaded=new TreeMap<String,JsonElement>();
            for(var holder:server.getRecipeManager().getRecipes())loaded.put(holder.id().identifier().toString(),Recipe.CODEC.encodeStart(ops,holder.value()).getOrThrow());
            check(baseline.keySet().equals(loaded.keySet()),"equipment upgrade keeps all loaded recipe IDs");
            for(var entry:baseline.entrySet()){
                var actual=loaded.get(entry.getKey());
                if(entry.getValue().equals(actual))continue;
                var original=restored.contains(entry.getKey())?RecipeAudit.before.get(entry.getKey()):entry.getValue();
                var transformed=RecipeTransform.transform(original.getAsJsonObject()).json();
                var expected=Recipe.CODEC.encodeStart(ops,Recipe.CODEC.parse(ops,transformed).getOrThrow()).getOrThrow();
                check(actual.equals(expected),"equipment upgrade changes only reviewed identities or restores original equipment layout "+entry.getKey());
                changed.add(entry.getKey());if(!restored.contains(entry.getKey()))identities.add(entry.getKey());
            }
            check(changed.containsAll(restored),"all fifteen old equipment layout patches are restored");
            var proof=new JsonObject();proof.addProperty("passed",true);var gson=new Gson();
            proof.add("changed_recipe_ids",gson.toJsonTree(changed));proof.add("identity_only_recipe_ids",gson.toJsonTree(identities));proof.add("restored_layout_ids",gson.toJsonTree(restored));
            Files.writeString(fixture.resolve("equipment-upgrade-proof.json"),gson.toJson(proof));
        }catch(IOException error){throw new IllegalStateException(error);}
    }
    static List<String> client(){
        var jei=local.sise.ores.compat.OresJeiPlugin.runtime;var visibility=jei.getJeiHelpers().getIngredientVisibility();var result=new ArrayList<String>();
        for(var group:MaterialCatalog.MATERIALS)if(group.form().equals("equipment")){
            if(!visibility.isIngredientVisible(mezz.jei.api.constants.VanillaTypes.ITEM_STACK,new ItemStack(item(group.target())),mezz.jei.api.ingredients.subtypes.UidContext.Ingredient))throw new IllegalStateException("Priority equipment hidden "+group.name());
            for(var source:group.members())if(visibility.isIngredientVisible(mezz.jei.api.constants.VanillaTypes.ITEM_STACK,new ItemStack(item(source)),mezz.jei.api.ingredients.subtypes.UidContext.Ingredient))throw new IllegalStateException("Duplicate equipment still visible "+source);
            result.add("JEI shows only priority native equipment "+group.name());
        }
        return result;
    }
    private EquipmentChecks(){}
}
