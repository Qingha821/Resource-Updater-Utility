package github.qingha.gui.gl;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import github.qingha.ResourceSynchronization;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import static com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR;

public class GlHelper {

    public static void clearScreen(float r, float g, float b) {
        RenderSystem.clearColor(r, g, b, 1f);
        RenderSystem.clear(16640, Minecraft.ON_OSX);
    }

    public static final float CORNER_RADIUS = 8.0f;
    public static final float SHADOW_SIZE = 4.0f;
    public static final float SHADOW_ALPHA = 0.2f;

    public static final int[] GRADIENT_COLORS = {
            0xff2ecc71,
            0xff3498db
    };

    private static Matrix4f lastProjectionMat;

    public static void initGlStates() {
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    public static void blitProgressRounded(
            float x, float y,
            float width, float height,
            float progressWidth,
            float radius,
            int color
    ) {
        float centerWidth = progressWidth - radius * 2;
        if (centerWidth > 0) {
            blit(x + radius, y, centerWidth, height, color);
        }
        float leftCircleProgress = Math.min(progressWidth, radius * 2);
        if (leftCircleProgress > 0) {
            enableScissor((int)x, (int)y, (int)leftCircleProgress, (int)height);
            blitRounded(x, y, radius * 2, height, radius, color);
            disableScissor();
        }
        if (progressWidth >= radius * 2) {
            float rightCircleWidth = Math.min(progressWidth - radius * 2, radius * 2);
            if (rightCircleWidth > 0) {
                float rightX = x + progressWidth - radius * 2;
                enableScissor((int)rightX, (int)y, (int)(radius * 2), (int)height);
                blitRounded(rightX, y, radius * 2, height, radius, color);
                disableScissor();
            }
        }
    }

    public static void blitGaussianBlur(float x, float y, float width, float height, float radius, int passes) {
        for (int i = 0; i < passes; i++) {
            float offset = (i + 1) * 2.5f;
            int alpha = (int)(18f / passes);
            int color = (alpha << 24) | 0xFFFFFF;
            blitRounded(x - offset, y - offset, width + offset * 2, height + offset * 2, radius + offset, color);
        }
    }

    public static void resetGlStates() {
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
    }

    public static final ResourceLocation PRELOAD_FONT_TEXTURE =
            new ResourceLocation(ResourceSynchronization.MOD_ID, "textures/font/harmony.png");
    public static final SimpleFont preloadFont = new SimpleFont(PRELOAD_FONT_TEXTURE);

    private static BufferBuilder bufferBuilder;

    public static void begin(ResourceLocation texture) {
        bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(GL11.GL_QUADS, new VertexFormat(POSITION_TEX_COLOR.getElements()));
        Minecraft.getInstance().getTextureManager().bind(texture);
    }

    public static void end() {
        Tesselator.getInstance().end();
    }

    public static void swapBuffer() throws MinecraftStoppingException {
        Window window = Minecraft.getInstance().getWindow();
        if (window.shouldClose()) {
            throw new MinecraftStoppingException();
        } else {
            window.updateDisplay();
        }
    }

    public static void blit(float x1, float y1, float width, float height, float u1, float v1, float u2, float v2, int color) {
        float x2 = x1 + width;
        float y2 = y1 + height;
        withColor(bufferBuilder.vertex(x1, y1, 1f).uv(u1, v1), color).endVertex();
        withColor(bufferBuilder.vertex(x2, y1, 1f).uv(u2, v1), color).endVertex();
        withColor(bufferBuilder.vertex(x2, y2, 1f).uv(u2, v2), color).endVertex();
        withColor(bufferBuilder.vertex(x1, y2, 1f).uv(u1, v2), color).endVertex();
    }

    public static void blit(float x1, float y1, float width, float height, int color) {
        float x2 = x1 + width;
        float y2 = y1 + height;
        withColor(bufferBuilder.vertex(x1, y1, 1f).uv(preloadFont.whiteU, preloadFont.whiteV), color).endVertex();
        withColor(bufferBuilder.vertex(x2, y1, 1f).uv(preloadFont.whiteU, preloadFont.whiteV), color).endVertex();
        withColor(bufferBuilder.vertex(x2, y2, 1f).uv(preloadFont.whiteU, preloadFont.whiteV), color).endVertex();
        withColor(bufferBuilder.vertex(x1, y2, 1f).uv(preloadFont.whiteU, preloadFont.whiteV), color).endVertex();
    }

    public static void blitRounded(float x, float y, float width, float height, float radius, int color) {
        blit(x + radius, y, width - radius * 2, height, color);
        blit(x, y + radius, radius, height - radius * 2, color);
        blit(x + width - radius, y + radius, radius, height - radius * 2, color);

        if (width <= 0 || height <= 0 || radius <= 0) return;

        float CORNER_DETAIL = 0.25f;
        for (float i = 0; i <= radius; i += CORNER_DETAIL) {
            for (float j = 0; j <= radius; j += CORNER_DETAIL) {
                float distance = (float) Math.sqrt(i * i + j * j);
                if (distance <= radius + CORNER_DETAIL) {
                    float alpha = smoothstep(radius + CORNER_DETAIL, radius - CORNER_DETAIL, distance);
                    alpha *= ((color >> 24) & 0xFF) / 255f;
                    int alphaColor = (color & 0x00FFFFFF) | ((int)(alpha * 255) << 24);

                    blit(x + radius - i - CORNER_DETAIL, y + radius - j - CORNER_DETAIL,
                            CORNER_DETAIL, CORNER_DETAIL, alphaColor);
                    blit(x + width - radius + i, y + radius - j - CORNER_DETAIL,
                            CORNER_DETAIL, CORNER_DETAIL, alphaColor);
                    blit(x + radius - i - CORNER_DETAIL, y + height - radius + j,
                            CORNER_DETAIL, CORNER_DETAIL, alphaColor);
                    blit(x + width - radius + i, y + height - radius + j,
                            CORNER_DETAIL, CORNER_DETAIL, alphaColor);
                }
            }
        }
    }

    public static void blitShadow(float x, float y, float width, float height, float radius, float shadowSize, int shadowColor) {
        float SHADOW_STEPS = 4;
        float stepSize = shadowSize * 0.25f;

        for (float i = SHADOW_STEPS; i > 0; i--) {
            float currentSize = i * stepSize;
            float alpha = (float) (0.25f * Math.pow(1 - (i - 1) / SHADOW_STEPS, 2));
            int currentColor = (shadowColor & 0x00FFFFFF) | ((int)(alpha * 255) << 24);

            blitRounded(
                    x - currentSize,
                    y - currentSize,
                    width + currentSize * 2,
                    height + currentSize * 2,
                    radius + currentSize * 0.5f,
                    currentColor
            );
        }
    }

    public static float smoothstep(float edge0, float edge1, float x) {
        x = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return x * x * (3 - 2 * x);
    }

    public static int interpolateColor(int color1, int color2, float factor) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        int a1 = (color1 >> 24) & 0xFF;

        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        int a2 = (color2 >> 24) & 0xFF;

        int r = (int)(r1 + (r2 - r1) * factor);
        int g = (int)(g1 + (g2 - g1) * factor);
        int b = (int)(b1 + (b2 - b1) * factor);
        int a = (int)(a1 + (a2 - a1) * factor);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void blitProgressBar(float x, float y, float width, float height, float progress, float v, int startColor, int endColor) {
        blitRounded(x, y, width, height, height / 2, 0x22000000);

        if (progress > 0) {
            float progressWidth = width * progress;
            float radius = height / 2;

            int steps = Math.max(1, (int)(progressWidth * 2));
            for (float i = 0; i < steps; i += 0.5f) {
                float factor = i / steps;
                int color = interpolateColor(startColor, endColor, factor);
                float segWidth = progressWidth / steps;

                float currentX = x + i * segWidth;
                if (i == 0) {
                    blitRounded(currentX, y, radius * 2, height, radius, color);
                } else if (i >= steps - 1) {
                    blitRounded(currentX, y, radius * 2, height, radius, color);
                } else {
                    blit(currentX, y, segWidth, height, color);
                }
            }

            float glowHeight = height * 0.3f;
            float glowY = y + height * 0.1f;
            int highlightColor = 0x33ffffff;
            blitRounded(x, glowY, progressWidth, glowHeight, glowHeight / 2, highlightColor);
        }
    }

    public static void drawShadowString(float x1, float y1, float width, float height, float fontSize,
                                        String text, int color, boolean monospace, boolean noWrap) {
        drawString(x1 + fontSize / 16, y1 + fontSize / 16, width, height, fontSize, text, 0xFF222222, monospace, noWrap);
        drawString(x1, y1, width, height, fontSize, text, color, monospace, noWrap);
    }

    public static void drawString(float x1, float y1, float width, float height, float fontSize,
                                  String text, int color, boolean monospace, boolean noWrap) {
        float CHAR_SPACING = 0f;
        float LINE_SPACING = 0.25f;

        var x = x1;
        var y = y1;
        for (char chr : text.toCharArray()) {
            if (chr == '\n') {
                y += fontSize + LINE_SPACING * fontSize;
                x = x1;
            } else if (chr == '\r') {
            } else if (chr == '\t') {
                float alignToPixels = (preloadFont.spaceWidthPl + CHAR_SPACING) * 8 * fontSize;
                x = (float) (Math.ceil((x - x1) / alignToPixels) * alignToPixels + x1);
            } else if (chr == ' ') {
                x += (preloadFont.spaceWidthPl + CHAR_SPACING) * fontSize;
            } else {
                SimpleFont.GlyphProperty glyph = preloadFont.getGlyph(chr);
                float advance = glyph.advancePl * fontSize;

                if (x + advance + CHAR_SPACING * fontSize > x1 + width) {
                    if (noWrap) {
                        continue;
                    } else {
                        y += fontSize + LINE_SPACING * fontSize;
                        x = x1;
                    }
                }
                if (y + fontSize > y1 + height) {
                    return;
                }

                blit(x + glyph.offsetXPl * fontSize, y + (preloadFont.baseLineYPl + glyph.offsetYPl) * fontSize,
                        glyph.widthPl * fontSize, glyph.heightPl * fontSize,
                        glyph.u1, glyph.v1, glyph.u2, glyph.v2, color);
                x += advance + CHAR_SPACING * fontSize;
            }
        }
    }

    public static float getStringWidth(String text, float fontSize) {
        float CHAR_SPACING = 0f;

        float width = 0;
        float x = 0;
        for (char chr : text.toCharArray()) {
            if (chr == '\n') {
                width = Math.max(width, x);
                x = 0;
            } else if (chr == '\r') {
            } else if (chr == '\t') {
                float alignToPixels = (preloadFont.spaceWidthPl + CHAR_SPACING) * 8 * fontSize;
                x = (float) (Math.ceil(x / alignToPixels) * alignToPixels);
            } else if (chr == ' ') {
                x += (preloadFont.spaceWidthPl + CHAR_SPACING) * fontSize;
            } else {
                SimpleFont.GlyphProperty glyph = preloadFont.getGlyph(chr);
                x += glyph.advancePl * fontSize + CHAR_SPACING * fontSize;
            }
        }
        return Math.max(width, x);
    }

    public static void setMatScaledPixel() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, getWidth(), getHeight(), 0, 1000, 3000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0, 0, -2000);
    }

    public static void enableScissor(float x, float y, float width, float height) {
        if (width <= 0 || height <= 0) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            return;
        }
        int winHeight = Minecraft.getInstance().getWindow().getHeight();
        int scissorX = Math.round(x);
        int scissorY = winHeight - Math.round(y + height);
        int scissorW = Math.round(width);
        int scissorH = Math.round(height);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, scissorW, scissorH);
    }

    public static void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    public static int getWidth() {
        int rawWidth = Minecraft.getInstance().getWindow().getWidth();
        if (rawWidth < 854) {
            return rawWidth;
        } else if (rawWidth < 1920) {
            return (int)((rawWidth - 854) * 1f / (1920 - 854) * (1366 - 854) + 854);
        } else {
            return 1366;
        }
    }

    public static int getHeight() {
        int rawWidth = Minecraft.getInstance().getWindow().getWidth();
        int rawHeight = Minecraft.getInstance().getWindow().getHeight();
        return (int)(rawHeight * (getWidth() * 1f / rawWidth));
    }

    public static void setMatCenterForm(float width, float height, float widthPercent) {
        int guiWidth = getWidth();
        int guiHeight = getHeight();
        float formWidth = guiWidth * widthPercent;
        float formHeight = height / width * formWidth;
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, guiWidth, guiHeight, 0, 1000, 3000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef((guiWidth - formWidth) / 2f, (guiHeight - formHeight) / 2f, -2000);
        GL11.glScalef(formWidth / width, formHeight / height, 1.0f);
    }

    private static VertexConsumer withColor(VertexConsumer vc, int color) {
        int a = color >>> 24 & 0xFF;
        int r = color >>> 16 & 0xFF;
        int g = color >>> 8 & 0xFF;
        int b = color & 0xFF;
        return vc.color(r, g, b, a);
    }

    public static String trimStringToWidth(String text, float maxWidth, float fontSize) {
        float width = 0;
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            width += GlHelper.getStringWidth(String.valueOf(c), fontSize);
            if (width > maxWidth - GlHelper.getStringWidth("...", fontSize)) {
                sb.append("...");
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public static class MinecraftStoppingException extends RuntimeException {
        public MinecraftStoppingException() {
            super("Minecraft is now stopping.");
        }
    }
}