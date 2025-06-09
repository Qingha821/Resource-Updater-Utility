package github.qingha.gui.gl;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class PreloadTextureResource implements Resource {

        private final ResourceLocation resourceLocation;

        public PreloadTextureResource(ResourceLocation resourceLocation) {
            this.resourceLocation = resourceLocation;
        }

        @Override
        public @NotNull ResourceLocation getLocation() {
            return resourceLocation;
        }

        @Override
        public @NotNull InputStream getInputStream() {
            return Objects.requireNonNull(getClass().getResourceAsStream("/assets/" + resourceLocation.getNamespace()
                    + "/" + resourceLocation.getPath()));
        }

        @Override
        public boolean hasMetadata() {
            return false;
        }

        @Override
        public <T> T getMetadata(MetadataSectionSerializer<T> metadataSectionSerializer) {
            return null;
        }

        @Override
        public @NotNull String getSourceName() {
            return resourceLocation.toDebugFileName();
        }

        @Override
        public void close() throws IOException {

        }
    }