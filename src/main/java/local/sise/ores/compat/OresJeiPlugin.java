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

/** Hide old identities in the browser only; legacy conversion recipes stay inspectable. */
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
        jei.getJeiHelpers().getIngredientVisibility().hideIngredients(VanillaTypes.ITEM_STACK, legacy, List.of(UidContext.Ingredient));
        runtime = jei;
    }
    @Override public void onRuntimeUnavailable() { runtime = null; }
}
