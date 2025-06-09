package github.qingha.drm;

import com.google.gson.JsonObject;
import github.qingha.ResourceSynchronizationClient;
import github.qingha.gui.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class ServerLockRegistry {

    public static boolean lockAllSyncedPacks = true;

    private static String localServerLock;

    private static String remoteServerLock;
    private static String packAppliedServerLock;

    private static boolean serverLockPrefetched = false;

    public static void updateLocalServerLock(File rpFolder) {
        if (lockAllSyncedPacks) {
            localServerLock = null; // So that when no longer lockAllSyncedPacks, the pack will reload
            return;
        }
        try {
            JsonObject metaObj = ResourceSynchronizationClient.JSON_PARSER.parse(IOUtils.toString(
                    AssetEncryption.wrapInputStream(new FileInputStream(rpFolder.toPath().resolve("pack.mcmeta").toFile()))
                    , StandardCharsets.UTF_8)).getAsJsonObject();
            if (metaObj.has("zbx_rpu_server_lock")) {
                localServerLock = metaObj.get("zbx_rpu_server_lock").getAsString();
                if (!serverLockPrefetched) {
                    remoteServerLock = localServerLock;
                    packAppliedServerLock = remoteServerLock;
                    ResourceSynchronizationClient.LOGGER.info("Server lock info prefetched from local pack.");
                    serverLockPrefetched = true;
                }
            } else {
                localServerLock = null;
            }
        } catch (Exception ignored) {
            localServerLock = null;
        }
    }

    public static boolean shouldRefuseProvidingFile(String resourcePath) {
        if (Objects.equals(resourcePath, "pack.mcmeta") || Objects.equals(resourcePath, "pack.png")) return false;
        if (lockAllSyncedPacks) return true;
        if (localServerLock == null) return false;
        return !Objects.equals(localServerLock, remoteServerLock);
    }

    public static void onLoginInitiated() {
        remoteServerLock = null;
    }

    public static void onSetServerLock(String serverLock) {
        remoteServerLock = serverLock;
    }

    public static void onAfterSetServerLock() {
        if (lockAllSyncedPacks) {
            Minecraft.getInstance().getToasts().addToast(new SystemToast(SystemToast.SystemToastIds.PACK_LOAD_FAILURE,
                    Text.literal("Synced Resource Pack Incomplete and Thus not Used"), Text.literal("Press F3+T to download again. Ask the staff when error.")
            ));
        }
        if (localServerLock == null) {
            ResourceSynchronizationClient.LOGGER.info("Asset coordination not required.");
        } else if (remoteServerLock == null) {
            ResourceSynchronizationClient.LOGGER.info("Asset coordination received no cooperation.");
        } else if (!remoteServerLock.equals(localServerLock)) {
            ResourceSynchronizationClient.LOGGER.info("Asset coordination received discrepancy.");
        } else if (lockAllSyncedPacks) {
            ResourceSynchronizationClient.LOGGER.info("Asset coordination is unavailable for incompleteness.");
        } else {
            ResourceSynchronizationClient.LOGGER.info("Asset coordination is applicable.");
        }
        if (localServerLock != null && !Objects.equals(packAppliedServerLock, remoteServerLock)) {
            packAppliedServerLock = remoteServerLock;
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().reloadResourcePacks());
        }
    }
}