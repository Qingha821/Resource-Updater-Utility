package github.qingha.gui.gl;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;

public class PreloadTextureResource implements Resource {

    private final ResourceLocation resourceLocation;

    public PreloadTextureResource(ResourceLocation resourceLocation) {
        this.resourceLocation = resourceLocation;
    }

    @Override
    public ResourceLocation getLocation() {
        return resourceLocation;
    }

    @Override
    public InputStream getInputStream() {
        return getClass().getResourceAsStream("/assets/" + resourceLocation.getNamespace()
                + "/" + resourceLocation.getPath());
    }

    @Override
    public <T> T getMetadata(MetadataSectionSerializer<T> metadataSectionSerializer) {
        return null;
    }

    @Override
    public String getSourceName() {
        return resourceLocation.toString();
    }

    @Override
    public void close() {
    }
}