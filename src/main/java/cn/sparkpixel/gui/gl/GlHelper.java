package cn.sparkpixel.gui.gl;

import cn.sparkpixel.ResourceUpdaterUtility;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class GlHelper {

    public static void clearScreen(float r, float g, float b) {
        RenderSystem.clearColor(r, g, b, 1f);
        RenderSystem.clear(16640, Minecraft.ON_OSX);
    }

    public static final float CORNER_RADIUS = 8.0f;
    public static final float SHADOW_SIZE = 4.0f;
    public static final float SHADOW_ALPHA = 0.2f;

    // 渐变色常量
    public static final int[] GRADIENT_COLORS = {
            0xff2ecc71, // 绿色
            0xff3498db  // 蓝色
    };
    private static ShaderInstance previousShader;
    private static Matrix4f lastProjectionMat;
    private static VertexSorting lastVertexSorting;

    public static void initGlStates() {
        previousShader = RenderSystem.getShader();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.getModelViewStack().pushPose();
        RenderSystem.getModelViewStack().setIdentity();
        RenderSystem.applyModelViewMatrix();
        lastProjectionMat = RenderSystem.getProjectionMatrix();
        lastVertexSorting = RenderSystem.getVertexSorting();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
    }

    public static void blitProgressRounded(
            float x, float y,
            float width, float height,
            float progressWidth, // 实际进度宽度
            float radius,
            int color
    ) {
        // 主体中间部分（自动适配左右圆角）
        float centerWidth = progressWidth - radius * 2;
        if (centerWidth > 0) {
            blit(x + radius, y, centerWidth, height, color);
        }

        // 左圆角区域（始终绘制）
        float leftCircleProgress = Math.min(progressWidth, radius * 2);
        if (leftCircleProgress > 0) {
            // 左半圆裁剪
            enableScissor((int)x, (int)y, (int)leftCircleProgress, (int)height);
            blitRounded(x, y, radius * 2, height, radius, color);
            disableScissor();
        }

        // 右圆角区域（当进度足够时）
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

    public static void resetGlStates() {
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShader(() -> previousShader);
        RenderSystem.setProjectionMatrix(lastProjectionMat, lastVertexSorting);
    }

    public static final ResourceLocation PRELOAD_FONT_TEXTURE =
            new ResourceLocation(ResourceUpdaterUtility.MOD_ID, "textures/font/harmony.png");
    public static final SimpleFont preloadFont = new SimpleFont(PRELOAD_FONT_TEXTURE);

    private static BufferBuilder bufferBuilder;

    public static void begin(ResourceLocation texture) {
        bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        Minecraft.getInstance().getTextureManager().getTexture(texture).setFilter(true, false);
        RenderSystem.setShaderTexture(0, texture);
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
        // 主体部分
        blit(x + radius, y, width - radius * 2, height, color);
        // 左右边缘
        blit(x, y + radius, radius, height - radius * 2, color);
        blit(x + width - radius, y + radius, radius, height - radius * 2, color);

        if (width <= 0 || height <= 0 || radius <= 0) return;

        // 修改圆角绘制逻辑（原始代码基础上）
        float effectiveRadius = Math.min(radius, Math.min(width/2, height/2));

        // 圆角部分使用更精细的渲染
        float CORNER_DETAIL = 0.25f;
        for (float i = 0; i <= radius; i += CORNER_DETAIL) {
            for (float j = 0; j <= radius; j += CORNER_DETAIL) {
                float distance = (float) Math.sqrt(i * i + j * j);
                if (distance <= radius + CORNER_DETAIL) {
                    // 使用平滑过渡
                    float alpha = smoothstep(radius + CORNER_DETAIL, radius - CORNER_DETAIL, distance);
                    alpha *= ((color >> 24) & 0xFF) / 255f;
                    int alphaColor = (color & 0x00FFFFFF) | ((int)(alpha * 255) << 24);

                    // 四个角落使用相同的细节级别
                    // 左上
                    blit(x + radius - i - CORNER_DETAIL, y + radius - j - CORNER_DETAIL,
                            CORNER_DETAIL, CORNER_DETAIL, alphaColor);
                    // 右上
                    blit(x + width - radius + i, y + radius - j - CORNER_DETAIL,
                            CORNER_DETAIL, CORNER_DETAIL, alphaColor);
                    // 左下
                    blit(x + radius - i - CORNER_DETAIL, y + height - radius + j,
                            CORNER_DETAIL, CORNER_DETAIL, alphaColor);
                    // 右下
                    blit(x + width - radius + i, y + height - radius + j,
                            CORNER_DETAIL, CORNER_DETAIL, alphaColor);
                }
            }
        }
    }

    // 新增绘制阴影方法
    public static void blitShadow(float x, float y, float width, float height, float radius, float shadowSize, int shadowColor) {
        float SHADOW_STEPS = 4; // 增加阴影层数
        float stepSize = shadowSize * 0.25f;

        for (float i = SHADOW_STEPS; i > 0; i--) {
            float currentSize = i * stepSize;
            // 使用二次方衰减获得更自然的阴影
            float alpha = (float) (0.25f * Math.pow(1 - (i - 1) / SHADOW_STEPS, 2));
            int currentColor = (shadowColor & 0x00FFFFFF) | ((int)(alpha * 255) << 24);

            blitRounded(
                    x - currentSize,
                    y - currentSize,
                    width + currentSize * 2,
                    height + currentSize * 2,
                    radius + currentSize * 0.5f, // 阴影圆角渐进收敛
                    currentColor
            );
        }
    }

    public static float smoothstep(float edge0, float edge1, float x) {
        x = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return x * x * (3 - 2 * x);
    }

    private static float clamp(float x, float min, float max) {
        return Math.max(min, Math.min(max, x));
    }

    // 新增颜色插值方法
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

    public static void blitGaussianBlur(float x, float y, float width, float height, float radius, int passes) {
        for (int i = 0; i < passes; i++) {
            float offset = (i + 1) * 2.5f;
            int alpha = (int)(18f / passes); // 总体透明度约0.07
            int color = (alpha << 24) | 0xFFFFFF;
            blitRounded(x - offset, y - offset, width + offset * 2, height + offset * 2, radius + offset, color);
        }
    }

    // 字符串截断
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

    public static void blitProgressBar(float x, float y, float width, float height, float progress, float v, int startColor, int endColor) {
        // 背景
        blitRounded(x, y, width, height, height/2, 0x22000000);

        if (progress > 0) {
            float progressWidth = width * progress;
            float radius = height/2;

            // 渐变填充
            int steps = Math.max(1, (int)(progressWidth * 2));
            for (float i = 0; i < steps; i += 0.5f) {
                float factor = i / steps;
                int color = interpolateColor(startColor, endColor, factor);
                float segWidth = progressWidth / steps;

                float currentX = x + i * segWidth;
                if (i == 0) {
                    // 左边圆角
                    blitRounded(currentX, y, radius * 2, height, radius, color);
                } else if (i >= steps - 1) {
                    // 右边圆角
                    blitRounded(currentX, y, radius * 2, height, radius, color);
                } else {
                    // 中间部分
                    blit(currentX, y, segWidth, height, color);
                }
            }

            // 添加光泽效果
            float glowHeight = height * 0.3f;
            float glowY = y + height * 0.1f;
            int highlightColor = 0x33ffffff;
            blitRounded(x, glowY, progressWidth, glowHeight, glowHeight/2, highlightColor);
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
                // Ignore CR
            } else if (chr == '\t') {
                // Align to 10 spaces
                float alignToPixels = (preloadFont.spaceWidthPl + CHAR_SPACING) * 10 * fontSize;
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
                // Ignore CR
            } else if (chr == '\t') {
                // Align to 10 spaces
                float alignToPixels = (preloadFont.spaceWidthPl + CHAR_SPACING) * 10 * fontSize;
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

    public static void setMatIdentity() {
        RenderSystem.getModelViewStack().setIdentity();
    }

    public static void setMatPixel() {
        Matrix4f matrix = new Matrix4f();
        matrix.scale(2, -2, 1);
        matrix.translate(-0.5f, -0.5f, 0);
        float rawWidth = Minecraft.getInstance().getWindow().getWidth();
        float rawHeight = Minecraft.getInstance().getWindow().getHeight();
        matrix.scale(1 / rawWidth, 1 / rawHeight, 1);
        RenderSystem.setProjectionMatrix(matrix, VertexSorting.ORTHOGRAPHIC_Z);
    }

    public static void setMatScaledPixel() {
        Matrix4f matrix = new Matrix4f();
        matrix.scale(2, -2, 1);
        matrix.translate(-0.5f, -0.5f, 0);
        matrix.scale(1f / getWidth(), 1f / getHeight(), 1);
        RenderSystem.setProjectionMatrix(matrix, VertexSorting.ORTHOGRAPHIC_Z);
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
        Matrix4f matrix = new Matrix4f();
        matrix.scale(2, -2, 1);
        matrix.translate(-0.5f, -0.5f, 0);
        float rawWidth = Minecraft.getInstance().getWindow().getWidth();
        float rawHeight = Minecraft.getInstance().getWindow().getHeight();
        matrix.scale(1f / rawWidth, 1f / rawHeight, 1);
        float formRawWidth = rawWidth * widthPercent;
        float formRawHeight = height / width * formRawWidth;
        matrix.translate((rawWidth - formRawWidth) / 2f, (rawHeight - formRawHeight) / 2f, 0);
        matrix.scale(formRawWidth / width, formRawHeight / height, 1);
        RenderSystem.setProjectionMatrix(matrix, VertexSorting.ORTHOGRAPHIC_Z);
    }

    public static void enableScissor(float x, float y, float width, float height) {
        // 新增边界检查
        if (width <= 0 || height <= 0) {
            RenderSystem.disableScissor();
            return;
        }

        // 保持原有坐标转换逻辑
        Matrix4f posMap = RenderSystem.getProjectionMatrix();
        Vector3f bottomLeft = posMap.transformPosition(new Vector3f(x, y + height, 0));
        Vector3f topRight = posMap.transformPosition(new Vector3f(x + width, y, 0));

        // 添加0.5f偏移避免像素间隙
        float x1 = Mth.map(bottomLeft.x, -1, 1, 0, Minecraft.getInstance().getWindow().getWidth()) + 0.5f;
        float y1 = Mth.map(bottomLeft.y, -1, 1, 0, Minecraft.getInstance().getWindow().getHeight()) + 0.5f;
        float x2 = Mth.map(topRight.x, -1, 1, 0, Minecraft.getInstance().getWindow().getWidth()) - 0.5f;
        float y2 = Mth.map(topRight.y, -1, 1, 0, Minecraft.getInstance().getWindow().getHeight()) - 0.5f;

        RenderSystem.enableScissor(
                (int)Math.floor(x1),
                (int)Math.floor(y1),
                (int)Math.ceil(x2 - x1),
                (int)Math.ceil(y2 - y1)
        );
    }

    public static void blitHalfCircle(
            float x, float y,
            float width, float height,
            float radius,
            int color,
            boolean isLeft
    ) {
        // 安全校验
        if (width <= 0 || height <= 0 || radius <= 0) return;

        // 主体矩形部分
        blit(x + (isLeft ? radius : 0), y, width - radius, height, color);

        // 半圆部分绘制（优化版）
        float CORNER_DETAIL = 0.5f;
        for (float i = 0; i <= radius; i += CORNER_DETAIL) {
            for (float j = 0; j <= radius; j += CORNER_DETAIL) {
                float distance = (float) Math.sqrt(i * i + j * j);
                if (distance <= radius) {
                    float alpha = smoothstep(radius, radius - CORNER_DETAIL, distance);
                    alpha *= ((color >> 24) & 0xFF) / 255f;
                    int alphaColor = (color & 0x00FFFFFF) | ((int)(alpha * 255) << 24);

                    // 仅绘制单侧半圆
                    if (isLeft) {
                        // 左半圆
                        blit(x + radius - i, y + radius - j, CORNER_DETAIL, CORNER_DETAIL, alphaColor); // 左上
                        blit(x + radius - i, y + height - radius + j - CORNER_DETAIL, CORNER_DETAIL, CORNER_DETAIL, alphaColor); // 左下
                    } else {
                        // 右半圆
                        blit(x + width - radius + i - CORNER_DETAIL, y + radius - j, CORNER_DETAIL, CORNER_DETAIL, alphaColor); // 右上
                        blit(x + width - radius + i - CORNER_DETAIL, y + height - radius + j - CORNER_DETAIL, CORNER_DETAIL, CORNER_DETAIL, alphaColor); // 右下
                    }
                }
            }
        }
    }

    public static void disableScissor() {
        RenderSystem.disableScissor();
    }

    private static VertexConsumer withColor(VertexConsumer vc, int color) {
        int a = color >>> 24 & 0xFF;
        int r = color >>> 16 & 0xFF;
        int g = color >>> 8 & 0xFF;
        int b = color & 0xFF;
        return vc.color(r, g, b, a);
    }

    public static class MinecraftStoppingException extends RuntimeException {
        public MinecraftStoppingException() {
            super("Minecraft is now stopping.");
        }
    }
}
