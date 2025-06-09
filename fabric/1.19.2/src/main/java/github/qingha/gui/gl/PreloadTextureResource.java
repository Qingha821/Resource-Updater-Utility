package github.qingha.gui.gl;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.util.Objects;

public class PreloadTextureResource extends Resource {

        private final ResourceLocation resourceLocation;

        public PreloadTextureResource(ResourceLocation resourceLocation) {
            super(resourceLocation.toDebugFileName(), InputStream::nullInputStream);
            this.resourceLocation = resourceLocation;
        }
        @Override
        public @NotNull InputStream open() {
            return Objects.requireNonNull(getClass().getResourceAsStream("/assets/" + resourceLocation.getNamespace() + "/" + resourceLocation.getPath()));
        }
    }