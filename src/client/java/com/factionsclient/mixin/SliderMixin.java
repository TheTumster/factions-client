package com.factionsclient.mixin;

import java.util.Map;
import java.util.WeakHashMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;

import com.factionsclient.FactionsClient;
import com.factionsclient.ui.Theme;

/** Vanilla sliders (FOV, volume, render distance...) in the RelapsedMC style: dark track, gradient fill, glowing knob. */
@Mixin(AbstractSliderButton.class)
public abstract class SliderMixin {
	private static final Map<AbstractSliderButton, Float> HOVER = new WeakHashMap<>();

	@Shadow
	protected double value;

	@Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
	private void factionsclient$laGSlider(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (FactionsClient.CONFIG == null || !FactionsClient.CONFIG.restyleButtons) return;
		ci.cancel();
		AbstractSliderButton s = (AbstractSliderButton) (Object) this;
		int x = s.getX(), y = s.getY(), w = s.getWidth(), h = s.getHeight();
		float t = Theme.approach(HOVER.getOrDefault(s, 0f), s.isHoveredOrFocused() ? 1f : 0f, 14f);
		HOVER.put(s, t);

		Theme.glow(g, x, y, w, h, 0.45f * t);
		Theme.rect(g, x, y, w, h, 0xE0161026);
		int fill = (int) Math.round((w - 2) * this.value);
		Theme.gradient(g, x + 1, y + 1, Math.max(2, fill), h - 2, 0.55f + 0.35f * t);
		Theme.outline(g, x, y, w, h, Theme.lerp(0x40FFFFFF, 0x90FFFFFF, t));
		int kx = Math.max(x + 1, Math.min(x + w - 4, x + 1 + fill - 1));
		var font = Minecraft.getInstance().font;
		int tw = font.width(s.getMessage());
		int tx0 = x + w / 2 - tw / 2 - 2, tx1 = x + w / 2 + tw / 2 + 2;
		Theme.glow(g, kx, y, 3, h, 0.5f);
		if (kx + 3 > tx0 && kx < tx1) {
			// Knob is behind the label: only draw its caps so the text stays readable.
			g.fill(kx, y + 1, kx + 3, y + 4, 0xFFFFFFFF);
			g.fill(kx, y + h - 4, kx + 3, y + h - 1, 0xFFFFFFFF);
		} else {
			g.fill(kx, y + 1, kx + 3, y + h - 1, 0xFFFFFFFF);
		}
		g.drawCenteredString(font, s.getMessage(), x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
	}
}
