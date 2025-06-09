package github.qingha;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ResourceSynchronization implements ModInitializer {
	public static final String MOD_ID = "resource-synchronization";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final ResourceLocation SERVERLOCK_PACKET_ID = new ResourceLocation(MOD_ID, "serverlock");

	private static final Path CONFIG_PATH = Path.of("config", "resource-synchronization-server.json");

	@Override
	public void onInitialize() {
		try {
			if (!Files.exists(CONFIG_PATH)) {
				JsonObject obj = new JsonObject();
				obj.addProperty("serverLock", "default_server_lock_value");
				Files.createDirectories(CONFIG_PATH.getParent());
				Files.writeString(CONFIG_PATH, new GsonBuilder().setPrettyPrinting().create().toJson(obj), StandardCharsets.UTF_8);
			}
		} catch (Exception e) {
			LOGGER.error("Failed to create server config", e);
		}

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			try {
				String serverLock = "default_server_lock_value";
				if (Files.exists(CONFIG_PATH)) {
					String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
					serverLock = new JsonParser().parse(json).getAsJsonObject().get("serverLock").getAsString();
				}
				FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
				buf.writeUtf(serverLock);
				ServerPlayNetworking.send(handler.player, SERVERLOCK_PACKET_ID, buf);
			} catch (Exception e) {
				LOGGER.error("Failed to send serverLock", e);
			}
		});
	}
}