package cn.sparkpixel.mixin;

import cn.sparkpixel.ResourceUpdaterUtilityClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(at = @At("HEAD"), method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;")
    void reloadResourcePacks(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        ResourceUpdaterUtilityClient.dispatchSyncWork();
        ResourceUpdaterUtilityClient.modifyPackList();
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/PackRepository;openAllSelected()Ljava/util/List;"), method = "<init>")
    void ctor(GameConfig gameConfig, CallbackInfo ci) {
        ResourceUpdaterUtilityClient.dispatchSyncWork();
        ResourceUpdaterUtilityClient.modifyPackList();
    }
}
