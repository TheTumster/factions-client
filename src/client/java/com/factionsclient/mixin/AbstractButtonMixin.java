package com.factionsclient.mixin;

import java.util.Map;
import java.util.WeakHashMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;

import com.factionsclient.FactionsClient;
import com.factionsclient.ui.Theme;

/** Every vanilla button in every menu gets the laG look (dark glass, gradient + glow on hover). */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {
	private static final Map<AbstractButton, Float> HOVER = new WeakHashMap<>();

	@Inject(method = "renderDefaultSprite", at = @At("HEAD"), cancellable = true)
	private void factionsclient$laGButton(GuiGraphics g, CallbackInfo ci) {
		if (FactionsClient.CONFIG == null || !FactionsClient.CONFIG.restyleButtons) return;
		ci.cancel();
		AbstractButton b = (AbstractButton) (Object) this;
		int x = b.getX(), y = b.getY(), w = b.getWidth(), h = b.getHeight();
		boolean over = b.isActive() && b.isHoveredOrFocused();
		float t = Theme.approach(HOVER.getOrDefault(b, 0f), over ? 1f : 0f, 14f);
		HOVER.put(b, t);
		if (!b.isActive()) {
			Theme.rect(g, x, y, w, h, 0xC0101319);
			Theme.outline(g, x, y, w, h, 0x18FFFFFF);
			return;
		}
		Theme.glow(g, x, y, w, h, 0.6f * t);
		Theme.rect(g, x, y, w, h, 0xE0141A23);
		Theme.gradient(g, x, y, w, h, 0.35f + 0.65f * t);
		Theme.outline(g, x, y, w, h, Theme.lerp(0x40FFFFFF, 0x90FFFFFF, t));
	}
}
