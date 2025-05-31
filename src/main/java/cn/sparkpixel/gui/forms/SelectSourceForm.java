package cn.sparkpixel.gui.forms;

import cn.sparkpixel.ResourceUpdaterUtility;
import cn.sparkpixel.gui.gl.GlHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

import java.io.IOException;

import static cn.sparkpixel.gui.gl.GlHelper.CORNER_RADIUS;

public class SelectSourceForm implements GlScreenForm {

    public float selectSourceFormWidth = 500, selectSourceFormHeight = 400;

    int selectedIndex = -1;

    boolean countdownExpired = false;

    @Override
    public void render() {
        int sourceSize = ResourceUpdaterUtility.CONFIG.sourceList.value.size() + 1;
        selectSourceFormHeight = 30 + 30 + 30 + sourceSize * 30 + (sourceSize - 1) * 10;
        if (selectedIndex == -1) {
            selectedIndex = ResourceUpdaterUtility.CONFIG.sourceList.value.indexOf(ResourceUpdaterUtility.CONFIG.selectedSource.value);
        }
        if (selectedIndex == -1) {
            selectedIndex = 0;
        }

        GlHelper.setMatCenterForm(selectSourceFormWidth, selectSourceFormHeight, 0.6f);
        GlHelper.begin(GlHelper.PRELOAD_FONT_TEXTURE);
        GlScreenForm.drawShadowRect(selectSourceFormWidth, selectSourceFormHeight, 0xffdee6ea);

        GlHelper.drawString(20, 15, selectSourceFormWidth - 40, 50, 18,
                "请选择下载源服务器\n别担心，稍后你可重新到这里选择", 0xff222222, false, false);

        GlHelper.blitShadow(0, 0, selectSourceFormWidth, selectSourceFormHeight,
                CORNER_RADIUS, 8, 0x66000000);
        GlHelper.blitRounded(0, 0, selectSourceFormWidth, selectSourceFormHeight,
                CORNER_RADIUS, 0xffdee6ea);

        for (int i = 0; i < sourceSize; i++) {
            float btnX = 30;
            float btnY = 30 + 30 + i * 40;
            float btnWidth = selectSourceFormWidth - 60;
            float btnHeight = 30;

            if (i == selectedIndex) {
                GlHelper.blitRounded(btnX, btnY, btnWidth, btnHeight, 4, 0xff63a0c6);
            } else {
                GlHelper.blitRounded(btnX, btnY, btnWidth, btnHeight, 4, 0xffc0d2db);
            }

            String escBtnHint = "用方向键滚动查看；按 ENTER 继续启动 ";
            GlHelper.drawString(20, selectSourceFormHeight - 20, selectSourceFormWidth - 40, 16, 16, escBtnHint, 0xff222222, false, true);

            GlHelper.end();
        }
    }

    private int heldKey = -1;

    @Override
    public boolean shouldStopPausing() {
        var glfwWindow = Minecraft.getInstance().getWindow().getWindow();
        if (InputConstants.isKeyDown(glfwWindow, InputConstants.KEY_UP) || InputConstants.isKeyDown(glfwWindow, InputConstants.KEY_W)) {
            if (heldKey != InputConstants.KEY_UP && heldKey != InputConstants.KEY_W) {
                selectedIndex = Math.max(0, selectedIndex - 1);
                heldKey = InputConstants.KEY_UP;
            }
        } else if (InputConstants.isKeyDown(glfwWindow, InputConstants.KEY_DOWN) || InputConstants.isKeyDown(glfwWindow, InputConstants.KEY_S)) {
            if (heldKey != InputConstants.KEY_DOWN && heldKey != InputConstants.KEY_S) {
                selectedIndex = Math.min(ResourceUpdaterUtility.CONFIG.sourceList.value.size(), selectedIndex + 1);
                heldKey = InputConstants.KEY_DOWN;
            }
        } else if (InputConstants.isKeyDown(glfwWindow, InputConstants.KEY_RETURN)
            || InputConstants.isKeyDown(glfwWindow, InputConstants.KEY_SPACE)
            || InputConstants.isKeyDown(glfwWindow, InputConstants.KEY_RIGHT)
            || countdownExpired) {
            if (selectedIndex == ResourceUpdaterUtility.CONFIG.sourceList.value.size()) {
                throw new GlHelper.MinecraftStoppingException();
            }
            ResourceUpdaterUtility.CONFIG.selectedSource.value = ResourceUpdaterUtility.CONFIG.sourceList.value.get(selectedIndex);
            ResourceUpdaterUtility.CONFIG.selectedSource.isFromLocal = true;
            try {
                ResourceUpdaterUtility.CONFIG.save();
            } catch (IOException ignored) { }
            return true;
        } else {
            heldKey = -1;
        }
        return false;
    }

    @Override
    public void reset() {
        selectedIndex = -1;
        heldKey = -1;
        countdownExpired = false;
    }

    @Override
    public void printLog(String line) throws GlHelper.MinecraftStoppingException {

    }

    @Override
    public void amendLastLog(String postfix) throws GlHelper.MinecraftStoppingException {

    }

    @Override
    public void setProgress(float primary, float secondary) throws GlHelper.MinecraftStoppingException {

    }

    @Override
    public void setInfo(String value, String textValue) throws GlHelper.MinecraftStoppingException {

    }

    @Override
    public void setException(Exception exception) throws GlHelper.MinecraftStoppingException {

    }
}
