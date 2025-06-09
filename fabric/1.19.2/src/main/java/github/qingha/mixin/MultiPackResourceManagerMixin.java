package github.qingha.mixin;

import github.qingha.ResourceSynchronizationClient;
import github.qingha.gui.gl.PreloadTextureResource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(MultiPackResourceManager.class)
public class MultiPackResourceManagerMixin {
    @Inject(at = @At("HEAD"), method = "getResource", cancellable = true)
    void getResource(ResourceLocation resourceLocation, CallbackInfoReturnable<Optional<Resource>> cir) {
        if (resourceLocation.getNamespace().equals(ResourceSynchronizationClient.MOD_ID)) {
            cir.setReturnValue(Optional.of(new PreloadTextureResource(resourceLocation)));
            cir.cancel();
        }
    }
}
