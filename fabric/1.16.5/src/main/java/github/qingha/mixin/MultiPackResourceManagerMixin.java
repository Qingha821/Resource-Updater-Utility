package github.qingha.mixin;

import github.qingha.ResourceSynchronizationClient;
import github.qingha.gui.gl.PreloadTextureResource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.SimpleReloadableResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SimpleReloadableResourceManager.class)
public class MultiPackResourceManagerMixin {
    @Inject(at = @At("HEAD"), method = "getResource", cancellable = true)
        void getResource(ResourceLocation resourceLocation, CallbackInfoReturnable<Resource> cir) {
            if (resourceLocation.getNamespace().equals(ResourceSynchronizationClient.MOD_ID)) {
                cir.setReturnValue(new PreloadTextureResource(resourceLocation));
                        cir.cancel();
            }
        }
    }
