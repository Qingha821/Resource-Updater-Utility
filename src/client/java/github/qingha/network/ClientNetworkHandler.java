package github.qingha.network;

import github.qingha.ResourceSynchronizationClient;
import github.qingha.drm.ServerLockRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

public class ClientNetworkHandler {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                new ResourceLocation(ResourceSynchronizationClient.MOD_ID, "serverlock"),
                (client, handler, buf, responseSender) -> {
                    String serverLock = buf.readUtf();
                    client.execute(() -> {
                        ServerLockRegistry.onSetServerLock(serverLock);
                        String localLock = ServerLockRegistry.getLocalServerLock(ResourceSynchronizationClient.CONFIG.packBaseDirFile.value);
                        boolean shouldLoad = localLock != null && localLock.equals(serverLock);
                        boolean needReload = !ResourceSynchronizationClient.shouldLoadPack && shouldLoad;
                        ResourceSynchronizationClient.shouldLoadPack = shouldLoad;
                        ResourceSynchronizationClient.singleplayerHandled = false;
                        if (needReload) {
                            Minecraft.getInstance().reloadResourcePacks();
                        }
                    });
                });
    }
}