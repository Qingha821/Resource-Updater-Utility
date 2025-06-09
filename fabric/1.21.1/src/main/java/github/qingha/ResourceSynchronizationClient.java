package github.qingha;

import github.qingha.drm.ServerLockRegistry;
import github.qingha.gui.GlProgressScreen;
import github.qingha.gui.gl.GlHelper;
import github.qingha.io.Dispatcher;
import github.qingha.io.network.DummyTrustManager;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.server.packs.repository.PackRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ResourceSynchronizationClient implements ClientModInitializer {

	public static final GlProgressScreen GL_PROGRESS_SCREEN = new GlProgressScreen();
	public static final HttpClient HTTP_CLIENT;
	public static final String MOD_ID = "resource-synchronization";
	public static final Logger LOGGER = LogManager.getLogger("ResourceSynchronizationClient");
	public static String MOD_VERSION = "";
	public static final Config CONFIG = new Config();
	public static final JsonParser JSON_PARSER = new JsonParser();
	public static boolean singleplayerHandled = false;
	public static boolean shouldLoadPack = true;
	public static boolean isSyncing = false;
	public static boolean hasInitSync = false;

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
			MOD_VERSION = FabricLoader.getInstance().getModContainer(MOD_ID).get()
					.getMetadata().getVersion().getFriendlyString();
			try {
				CONFIG.load();
			} catch (IOException e) {
				LOGGER.error("Failed to load config", e);
			}

			ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			singleplayerHandled = false;
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level != null && client.getCurrentServer() == null) {
				if (!singleplayerHandled && shouldLoadPack) {
					shouldLoadPack = false;
					singleplayerHandled = true;
					Minecraft.getInstance().reloadResourcePacks();
				}
			} else {
				singleplayerHandled = false;
			}
		});
}

	public static void dispatchSyncWork() {
		GlHelper.initGlStates();

		while (true) {
			Dispatcher syncDispatcher = new Dispatcher();
			if (ResourceSynchronizationClient.CONFIG.selectedSource.value == null
					|| ResourceSynchronizationClient.CONFIG.selectedSource.value.baseUrl.isEmpty()) {
				if (ResourceSynchronizationClient.CONFIG.sourceList.value.size() > 1) {
					GL_PROGRESS_SCREEN.resetToSelectSource();
					try {
						GL_PROGRESS_SCREEN.shouldContinuePausing(true); {
						}
					} catch (GlHelper.MinecraftStoppingException ignored) {
						ServerLockRegistry.lockAllSyncedPacks = true;
						break;
					} catch (Exception ignored) {
					}
				} else if (ResourceSynchronizationClient.CONFIG.sourceList.value.size() == 1) {
					ResourceSynchronizationClient.CONFIG.selectedSource.value = ResourceSynchronizationClient.CONFIG.sourceList.value.get(0);
					ResourceSynchronizationClient.CONFIG.selectedSource.isFromLocal = true;
				} else {
					ResourceSynchronizationClient.CONFIG.selectedSource.value = new Config.SourceProperty(
							"NOT CONFIGURED",
							"",
							false, false, true
					);
				}
			}

			GL_PROGRESS_SCREEN.reset();
			try {
				boolean syncSuccess = syncDispatcher.runSync(ResourceSynchronizationClient.CONFIG.getPackBaseDir(),
						ResourceSynchronizationClient.CONFIG.selectedSource.value, GL_PROGRESS_SCREEN);
                ServerLockRegistry.lockAllSyncedPacks = !syncSuccess;

				Minecraft.getInstance().options.save();
				try {
					ResourceSynchronizationClient.CONFIG.save();
				} catch (IOException ignored) { }
				break;
			} catch (GlHelper.MinecraftStoppingException ignored) {
				ServerLockRegistry.lockAllSyncedPacks = true;
				ResourceSynchronizationClient.CONFIG.selectedSource.value = new Config.SourceProperty(
						"NOT CONFIGURED",
						"",
						false, false, true
				);
				if (ResourceSynchronizationClient.CONFIG.sourceList.value.size() <= 1) {
					break;
				}
			} catch (Exception ignored) {
				ServerLockRegistry.lockAllSyncedPacks = true;
				break;
			}
		}

		try {
			GL_PROGRESS_SCREEN.shouldContinuePausing(true);{
			}
		} catch (Exception ignored) { }

		ServerLockRegistry.updateLocalServerLock(ResourceSynchronizationClient.CONFIG.packBaseDirFile.value);
		GlHelper.resetGlStates();
	}

	public static void modifyPackList() {
		Options options = Minecraft.getInstance().options;
		String expectedEntry = "file/" + ResourceSynchronizationClient.CONFIG.localPackName.value;
		LOGGER.info("shouldLoadPack: {}", shouldLoadPack);
		if (shouldLoadPack) {
			if (!options.resourcePacks.contains(expectedEntry)) {
				options.resourcePacks.add(expectedEntry);
			}
			options.incompatibleResourcePacks.remove(expectedEntry);
		} else {
			options.resourcePacks.remove(expectedEntry);
		}

		if (!options.resourcePacks.contains("vanilla")) {
			options.resourcePacks.add("vanilla");
		}
		if (!options.resourcePacks.contains("Fabric Mods")) {
			options.resourcePacks.add("Fabric Mods");
		}

		PackRepository repository = Minecraft.getInstance().getResourcePackRepository();
		repository.reload();
		options.loadSelectedResourcePacks(repository);
	}
}
