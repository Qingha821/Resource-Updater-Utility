package github.qingha.gui.forms;

import github.qingha.gui.gl.GlHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public class ExceptionForm implements GlScreenForm {

    private static final float FORM_WIDTH = 680;
    private static final float FORM_HEIGHT = 360;
    private static final float ELEVATION = 6;
    private static final int CARD_BG_COLOR = 0xF5FFFFFF;
    private static final int TEXT_PRIMARY_COLOR = 0xFF2C3E50;
    private static final int TEXT_SECONDARY_COLOR = 0xFF7F8C8D;
    private static final int ACCENT_COLOR = 0xFFFF4D4F;

    private final List<String> logs = new ArrayList<>();
    private int logViewOffset = 0;
    private Exception exception;

    @Override
    public void render() {
        GlHelper.setMatCenterForm(FORM_WIDTH, FORM_HEIGHT, 0.7f);
        GlHelper.begin(GlHelper.PRELOAD_FONT_TEXTURE);
        GlHelper.blitShadow(0, 0, FORM_WIDTH, FORM_HEIGHT, GlHelper.CORNER_RADIUS, ELEVATION, 0x22000000);
        GlHelper.blitRounded(0, 0, FORM_WIDTH, FORM_HEIGHT, GlHelper.CORNER_RADIUS, CARD_BG_COLOR);
        GlHelper.drawString(40, 40, FORM_WIDTH - 80, 32, 26, "发生错误！", ACCENT_COLOR, false, true);
        int fontColor = System.currentTimeMillis() % 400 >= 200 ? 0xFFFFA940 : TEXT_SECONDARY_COLOR;
        GlHelper.drawString(40, 80, FORM_WIDTH - 80, 24, 16,
                "请报告管理员！按 ENTER 以在没有资源的情况下继续启动", fontColor, false, true);
        final int LOG_FONT_SIZE = 15;
        final int LOG_LINE_HEIGHT = 20;
        float logBegin = 120;
        float usableLogHeight = FORM_HEIGHT - logBegin - 40;
        int logLines = (int) Math.floor(usableLogHeight / LOG_LINE_HEIGHT);
        int linesToShow = Math.min(logLines, logs.size() - logViewOffset);

        for (int i = 0; i < linesToShow; i++) {
            GlHelper.drawString(40, logBegin + LOG_LINE_HEIGHT * i, FORM_WIDTH - 80, LOG_LINE_HEIGHT,
                    LOG_FONT_SIZE, logs.get(logViewOffset + i), TEXT_PRIMARY_COLOR, false, false);
        }
        GlHelper.end();
    }

    @Override
    public boolean shouldStopPausing() {
        var glfwWindow = Minecraft.getInstance().getWindow().getWindow();

        final int LOG_LINE_HEIGHT = 20;
        float logBegin = 120;
        float usableLogHeight = FORM_HEIGHT - logBegin - 40;
        int logLines = (int) Math.floor(usableLogHeight / LOG_LINE_HEIGHT);
        int maxLogViewOffset = Math.max(0, logs.size() - logLines);

        if (InputConstants.isKeyDown(glfwWindow, InputConstants.KEY_HOME)) {
            logViewOffset = 0;
        } else if (InputConstants.isKeyDown(glfwWindow, InputConstants.KEY_END)) {
            logViewOffset = maxLogViewOffset;
        } else if (InputConstants.isKeyDown(glfwWindow, InputConstants.KEY_PAGEUP)) {
            logViewOffset = Math.max(0, logViewOffset - logLines);
        } else if (InputConstants.isKeyDown(glfwWindow, InputConstants.KEY_PAGEDOWN)) {
            logViewOffset = Math.min(maxLogViewOffset, logViewOffset + logLines);
        } else if (InputConstants.isKeyDown(glfwWindow, InputConstants.KEY_UP)) {
            logViewOffset = Math.max(0, logViewOffset - 1);
        } else if (InputConstants.isKeyDown(glfwWindow, InputConstants.KEY_DOWN)) {
            logViewOffset = Math.min(maxLogViewOffset, logViewOffset + 1);
        }

        return InputConstants.isKeyDown(glfwWindow, InputConstants.KEY_RETURN);
    }

    @Override
    public void reset() {
        logs.clear();
        exception = null;
        logViewOffset = 0;
    }

    @Override
    public void printLog(String line) {
        logs.add(line);
        final int LOG_LINE_HEIGHT = 20;
        float logBegin = 120;
        float usableLogHeight = FORM_HEIGHT - logBegin - 40;
        int logLines = (int) Math.floor(usableLogHeight / LOG_LINE_HEIGHT);
        logViewOffset = Math.max(0, logs.size() - logLines);
    }

    @Override
    public void amendLastLog(String postfix) {
        logs.set(logs.size() - 1, logs.get(logs.size() - 1) + postfix);
    }

    @Override
    public void setProgress(float primary, float secondary) {}

    @Override
    public void setInfo(String value, String textValue) {}

    @Override
    public void setException(Exception exception) {
        this.exception = exception;
        printLog("");
        printLog("错误报告: ");
        for (String line : exception.toString().split("\n")) {
            printLog(line);
        }
    }
    public Exception getException() {
        return exception;
    }
}