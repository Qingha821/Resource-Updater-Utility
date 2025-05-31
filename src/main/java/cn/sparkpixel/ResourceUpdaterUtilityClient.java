package cn.sparkpixel;

import cn.sparkpixel.drm.ServerLockRegistry;
import cn.sparkpixel.gui.GlProgressScreen;
import cn.sparkpixel.gui.gl.GlHelper;
import cn.sparkpixel.io.Dispatcher;
import cn.sparkpixel.io.ModsDispatcher;
import cn.sparkpixel.io.network.DummyTrustManager;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.server.packs.repository.PackRepository;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static cn.sparkpixel.ResourceUpdaterUtility.CONFIG;
import static com.mojang.text2speech.Narrator.LOGGER;

public class ResourceUpdaterUtilityClient implements ClientModInitializer {

    public static final GlProgressScreen GL_PROGRESS_SCREEN = new GlProgressScreen();
    public static final HttpClient HTTP_CLIENT;

    static {
        final Properties props = System.getProperties();
        props.setProperty("jdk.internal.httpclient.disableHostnameVerification", Boolean.TRUE.toString());

        ExecutorService HTTP_CLIENT_EXECUTOR = Executors.newFixedThreadPool(4);
        HTTP_CLIENT = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .executor(HTTP_CLIENT_EXECUTOR)
                .sslContext(DummyTrustManager.UNSAFE_CONTEXT)
                .build();
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("Client mod initializing...");
    }

    public static void dispatchSyncWork() {
        GlHelper.initGlStates();

        while (true) {
            Dispatcher syncDispatcher = new Dispatcher();
            if (CONFIG.selectedSource.value == null
                || CONFIG.selectedSource.value.baseUrl.isEmpty()) {
                if (CONFIG.sourceList.value.size() > 1) {
                    GL_PROGRESS_SCREEN.resetToSelectSource();
                    try {
                        while (GL_PROGRESS_SCREEN.shouldContinuePausing(true)) {
                            Thread.sleep(50);
                        }
                    } catch (GlHelper.MinecraftStoppingException ignored) {
                        ServerLockRegistry.lockAllSyncedPacks = true;
                        break;
                    } catch (Exception ignored) {
                    }
                } else if (CONFIG.sourceList.value.size() == 1) {
                    CONFIG.selectedSource.value = CONFIG.sourceList.value.get(0);
                    CONFIG.selectedSource.isFromLocal = true;
                } else {
                    CONFIG.selectedSource.value = new Config.SourceProperty(
                            "NOT CONFIGURED",
                            "",
                            false, false, true
                    );
                }
            }

            GL_PROGRESS_SCREEN.reset();
            try {
                boolean syncSuccess = syncDispatcher.runSync(CONFIG.getPackBaseDir(),
                        CONFIG.selectedSource.value, GL_PROGRESS_SCREEN);
                if (syncSuccess) {
                    ServerLockRegistry.lockAllSyncedPacks = false;
                    try {
                        GL_PROGRESS_SCREEN.printLog("Getting mods list...");
                        String modsJson = syncDispatcher.fetchModsList(CONFIG.selectedSource.value.baseUrl);
                        if (modsJson != null && !modsJson.isEmpty()) {
                            dispatchModsSync(modsJson);
                        } else {
                            GL_PROGRESS_SCREEN.printLog("No mods list found.");
                        }
                    } catch (Exception e) {
                        GL_PROGRESS_SCREEN.printLog("Mods synchronization failed: " + e.getMessage());
                    }
                } else {
                    ServerLockRegistry.lockAllSyncedPacks = true;
                }

                Minecraft.getInstance().options.save();
                try {
                    CONFIG.save();
                } catch (IOException ignored) { }
                break;
            } catch (GlHelper.MinecraftStoppingException ignored) {
                ServerLockRegistry.lockAllSyncedPacks = true;
                CONFIG.selectedSource.value = new Config.SourceProperty(
                        "NOT CONFIGURED",
                        "",
                        false, false, true
                );
                if (CONFIG.sourceList.value.size() <= 1) {
                    break;
                }
            } catch (Exception ignored) {
                ServerLockRegistry.lockAllSyncedPacks = true;
                break;
            }
        }

        try {
            while (GL_PROGRESS_SCREEN.shouldContinuePausing(true)) {
                Thread.sleep(50);
            }
        } catch (Exception ignored) { }

        ServerLockRegistry.updateLocalServerLock(CONFIG.packBaseDirFile.value);
        GlHelper.resetGlStates();
    }

    public static void modifyPackList() {
        Options options = Minecraft.getInstance().options;
        String expectedEntry = "file/" + CONFIG.localPackName.value;
        options.resourcePacks.remove(expectedEntry);
        if (!options.resourcePacks.contains("vanilla")) {
            options.resourcePacks.add("vanilla");
        }
        if (!options.resourcePacks.contains("Fabric Mods")) {
            options.resourcePacks.add("Fabric Mods");
        }
        options.resourcePacks.add(expectedEntry);
        options.incompatibleResourcePacks.remove(expectedEntry);

        PackRepository repository = Minecraft.getInstance().getResourcePackRepository();
        repository.reload();
        options.loadSelectedResourcePacks(repository);
    }

    public static void dispatchModsSync(String jsonContent) {
        Path modsDir = Minecraft.getInstance().gameDirectory.toPath().resolve("mods");
        ModsDispatcher dispatcher = new ModsDispatcher(modsDir, CONFIG.selectedSource.value.baseUrl);

        GL_PROGRESS_SCREEN.reset();
        try {
            boolean syncSuccess = dispatcher.runSync(jsonContent, GL_PROGRESS_SCREEN);
            if (!syncSuccess) {
                GL_PROGRESS_SCREEN.printLog("Mods synchronization failed.");
            }
        } catch (Exception e) {
            GL_PROGRESS_SCREEN.setException(e);
        }
    }
}
