package local.sise.ores.compat.mixin;

import aztech.modern_industrialization.machines.recipe.*;
import local.sise.ores.RecipeSafety;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Manual choices must not become competing automatic MI cutting-machine recipes. */
@Mixin(value = RecipeConversions.class, remap = false)
public abstract class ManualStonecuttingMixin {
    @Inject(method = "ofStonecutting", at = @At("HEAD"), cancellable = true, remap = false)
    private static void siseOres$manualOnly(RecipeHolder<StonecutterRecipe> holder, MachineRecipeType type,
            RegistryAccess registries, CallbackInfoReturnable<RecipeHolder<MachineRecipe>> callback) {
        if (RecipeSafety.manualOnly(holder.id().identifier().toString())) callback.setReturnValue(null);
    }
}
