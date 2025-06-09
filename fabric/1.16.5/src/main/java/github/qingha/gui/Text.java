package github.qingha.gui;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;

public interface Text {
	static MutableComponent literal(String text) {
		return new TextComponent(text);
	}
}
