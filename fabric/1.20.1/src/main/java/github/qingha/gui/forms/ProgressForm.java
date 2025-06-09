package github.qingha.gui.forms;

import github.qingha.ResourceSynchronizationClient;
import github.qingha.gui.gl.GlHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.joml.Vector4f;

import java.util.LinkedList;

public class ProgressForm implements GlScreenForm {

    private static final float FORM_WIDTH = 680;
    private static final float FORM_HEIGHT = 360;
    private static final float ELEVATION = 6;
    private static final float ANIM_SPEED = 8.0f;
    private static final int CARD_BG_COLOR = 0xF5FFFFFF;
    private static final int PROGRESS_BG_COLOR = 0x22000000;
    private static final int TEXT_PRIMARY_COLOR = 0xFF2C3E50;
    private static final int TEXT_SECONDARY_COLOR = 0xFF7F8C8D;
    private static final int ACCENT_COLOR = 0xFF3498DB;

    private float primaryProgress;
    private float displayProgress;
    private String speedText = "";
    private String timeText = "";
    private String fileName = "";
    private long lastUpdateTime;
    private float animProgress;
    private final LinkedList<String> logLines = new LinkedList<>();
    private static final int MAX_LOG_LINES = 5;

    @Override
    public void render() {
        long currentTime = System.currentTimeMillis();
        float deltaTime = Math.min((currentTime - lastUpdateTime) / 1000f, 0.05f);
        lastUpdateTime = currentTime;

        displayProgress += (primaryProgress - displayProgress) * Math.min(1, deltaTime * ANIM_SPEED);
        displayProgress = Mth.clamp(displayProgress, 0, 1);

        GlHelper.setMatCenterForm(FORM_WIDTH, FORM_HEIGHT, 0.7f);

        GlHelper.begin(GlHelper.PRELOAD_FONT_TEXTURE);
        drawCardBackground(deltaTime);
        drawMainTitle();
        drawSubTitle();
        drawProgressSystem();
        drawInfoSection();
        drawLogSection();
        drawBottomHint();
        GlHelper.end();
    }

    private void drawCardBackground(float deltaTime) {
        animProgress = Mth.clamp(animProgress + ANIM_SPEED * 0.8f * deltaTime, 0, 1);
        float x = 16f * (1 - animProgress);
        float y = 16f * (1 - animProgress);
        float w = FORM_WIDTH - 2 * 16f * (1 - animProgress);
        float h = FORM_HEIGHT - 2 * 16f * (1 - animProgress);

        GlHelper.blitGaussianBlur(x, y, w, h, GlHelper.CORNER_RADIUS, 8);
        GlHelper.blitShadow(x, y, w, h, GlHelper.CORNER_RADIUS, ELEVATION, 0x22000000);
        GlHelper.blitRounded(x, y, w, h, GlHelper.CORNER_RADIUS, CARD_BG_COLOR);
    }

    private void drawMainTitle() {
        float maxWidth = FORM_WIDTH - 80f;
        String mainTitle = "资源下载中";
        String title = GlHelper.trimStringToWidth(mainTitle, maxWidth, 26);
        GlHelper.drawString(
                40f, 56f,
                maxWidth, 32,
                26, title, ACCENT_COLOR, false, true
        );
    }

    private void drawSubTitle() {
        float maxWidth = FORM_WIDTH - 80f;
        String subTitle = "日志显示...";
        String sub = GlHelper.trimStringToWidth(subTitle, maxWidth, 16);
        GlHelper.drawString(
                40f, 88f,
                maxWidth, 24,
                16, sub, TEXT_SECONDARY_COLOR, false, true
        );
    }

    private void drawProgressSystem() {
        float barWidth = FORM_WIDTH - 80f;
        float barY = 124f;
        float barRadius = 12f;

        GlHelper.blitRounded(40f, barY, barWidth, 24f, barRadius, PROGRESS_BG_COLOR);

        if (displayProgress > 0) {
            float progressWidth = barWidth * displayProgress;
            GlHelper.blitProgressRounded(40f, barY, barWidth, 24f, progressWidth, barRadius, ACCENT_COLOR);
        }

        String percentText = String.format("%d%%", Math.round(displayProgress * 100));
        float textWidth = GlHelper.getStringWidth(percentText, 18);
        float textX = 40f + barWidth * displayProgress - textWidth - 12;
        textX = Mth.clamp(textX, 40f + 8, 40f + barWidth - textWidth - 8);
        GlHelper.drawString(
                textX, barY + 2,
                textWidth + 16, 20,
                18, percentText, 0xFFFFFFFF, true, true
        );
    }

    private void drawInfoSection() {
        float y = 170f;
        float iconSize = 18f;
        float gap = 32f;
        float x = 40f;

        if (!fileName.isEmpty()) {
            drawIconWithText(x, y, '*', new Vector4f(0.2f, 0.6f, 0.9f, 1), "下载资源：" + fileName, 220);
            x += 320 + gap;
        }
        if (!speedText.isEmpty()) {
            drawIconWithText(x, y, '#', new Vector4f(0.3f, 0.69f, 0.49f, 1), "下载资源：" + speedText, 180);
            x += 160 + gap;
        }
        if (!timeText.isEmpty()) {
            drawIconWithText(x, y, '*', new Vector4f(0.9f, 0.49f, 0.13f, 1), "没下载的资源" + timeText, 999);
        }
        GlHelper.blit(40f, y + iconSize + 8, FORM_WIDTH - 10f, 1.5f, 0x1188AACC);
    }

    private void drawLogSection() {
        float y = 210f;
        float lineHeight = 20f;
        float maxWidth = FORM_WIDTH - 80f;
        int lines = Math.min(logLines.size(), MAX_LOG_LINES);
        int start = Math.max(0, logLines.size() - MAX_LOG_LINES);
        for (int i = 0; i < lines; i++) {
            String log = GlHelper.trimStringToWidth(logLines.get(start + i), maxWidth, 15);
            GlHelper.drawString(
                    40f, y + i * lineHeight,
                    maxWidth, lineHeight,
                    15, log, TEXT_PRIMARY_COLOR, false, false
            );
        }
    }

    private void drawBottomHint() {
        String hintText = ResourceSynchronizationClient.CONFIG.sourceList.value.size() > 1 ?
                "按下 ESC 取消或切换下载源" : "按下 ESC 取消下载";
        float textWidth = GlHelper.getStringWidth(hintText, 14);
        GlHelper.drawString(
                FORM_WIDTH - 40f - textWidth,
                FORM_HEIGHT - 32f,
                textWidth + 16, 20,
                14, hintText, TEXT_SECONDARY_COLOR, false, true
        );
    }

    private void drawIconWithText(float x, float y, int iconCode, Vector4f color, String text, float maxWidth) {
        GlHelper.drawString(
                x, y + 2, 20, 20, 16,
                String.valueOf((char)iconCode),
                colorToInt(color), true, true
        );
        String trimmed = GlHelper.trimStringToWidth(text, maxWidth - 24, 15);
        GlHelper.drawString(
                x + 24, y, maxWidth - 24, 20,
                15, trimmed, TEXT_PRIMARY_COLOR, false, false
        );
    }

    private static int colorToInt(Vector4f color) {
        return ((int)(color.w * 255) << 24) |
                ((int)(color.x * 255) << 16) |
                ((int)(color.y * 255) << 8) |
                ((int)(color.z * 255));
    }

    @Override
    public boolean shouldStopPausing() {
        if (InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), InputConstants.KEY_ESCAPE)) {
            throw new GlHelper.MinecraftStoppingException();
        }
        return true;
    }

    @Override
    public void reset() {
        primaryProgress = 0;
        displayProgress = 0;
        speedText = "";
        timeText = "";
        fileName = "";
        logLines.clear();
        lastUpdateTime = System.currentTimeMillis();
    }

    @Override
    public void printLog(String line) {
        if (logLines.size() >= 20) logLines.removeFirst();
        logLines.add(line);
    }

    @Override
    public void amendLastLog(String postfix) {
        if (!logLines.isEmpty()) {
            String last = logLines.removeLast();
            logLines.add(last + postfix);
        }
    }

    @Override
    public void setProgress(float primary, float secondary) {
        this.primaryProgress = primary;
    }

    @Override
    public void setInfo(String secondary, String textValue) {
        this.speedText = secondary;
        this.timeText = textValue;
    }

    @Override
    public void setException(Exception exception) {
        printLog("发生错误: " + exception.getMessage());
    }

    public void setFileName(String name) {
        this.fileName = name;
    }
}