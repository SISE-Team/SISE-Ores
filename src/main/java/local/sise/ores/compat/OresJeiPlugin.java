package local.sise.ores.compat;

import java.util.*;
import local.sise.ores.*;
import mezz.jei.api.*;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;

/** Hide legacy ingredient identities from the browser and recipe ingredient rotation. */
@JeiPlugin
public final class OresJeiPlugin implements IModPlugin {
    public static volatile IJeiRuntime runtime;
    @Override public Identifier getPluginUid() { return SiseOres.id("unification"); }
    @Override public void onRuntimeAvailable(IJeiRuntime jei) {
        var legacy = new ArrayList<ItemStack>();
        for (var source : MaterialCatalog.BY_SOURCE.keySet()) {
            var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(source));
            if (item != null && item != Items.AIR) legacy.add(new ItemStack(item));
        }
        jei.getJeiHelpers().getIngredientVisibility().hideIngredients(VanillaTypes.ITEM_STACK, legacy, List.of(UidContext.Ingredient,UidContext.Recipe));
        var oldFluids=jei.getIngredientManager().getAllIngredients(mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK).stream()
            .filter(stack->FluidCatalog.BY_SOURCE.containsKey(BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString())).toList();
        if(!oldFluids.isEmpty())jei.getJeiHelpers().getIngredientVisibility().hideIngredients(mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK,oldFluids,List.of(UidContext.Ingredient,UidContext.Recipe));
        var oldChemicals=jei.getIngredientManager().getAllIngredients(mekanism.client.recipe_viewer.jei.MekanismJEI.TYPE_CHEMICAL).stream()
            .filter(stack->FluidCatalog.CHEMICAL_BY_SOURCE.containsKey(mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.getKey(stack.getChemical()).toString())).toList();
        if(!oldChemicals.isEmpty())jei.getJeiHelpers().getIngredientVisibility().hideIngredients(mekanism.client.recipe_viewer.jei.MekanismJEI.TYPE_CHEMICAL,oldChemicals,List.of(UidContext.Ingredient,UidContext.Recipe));
        runtime = jei;
    }
    @Override public void onRuntimeUnavailable() { runtime = null; }
}
