package github.qingha.mixin;

import github.qingha.ResourceSynchronizationClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

import static github.qingha.ResourceSynchronizationClient.hasInitSync;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(at = @At("HEAD"), method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;")
        void reloadResourcePacks(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
            if (!ResourceSynchronizationClient.isSyncing) {
                ResourceSynchronizationClient.dispatchSyncWork();
                ResourceSynchronizationClient.modifyPackList();
        }
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/PackRepository;openAllSelected()Ljava/util/List;"), method = "<init>")
    void ctor(GameConfig gameConfig, CallbackInfo ci) {
        if (!ResourceSynchronizationClient.isSyncing && !hasInitSync) {
            hasInitSync = true;
            ResourceSynchronizationClient.dispatchSyncWork();
            ResourceSynchronizationClient.modifyPackList();
        }
    }
}
