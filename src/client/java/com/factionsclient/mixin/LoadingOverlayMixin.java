package com.factionsclient.mixin;

import java.util.function.IntSupplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import com.factionsclient.ui.SplashLogoTexture;
import com.factionsclient.ui.Theme;

/**
 * Replaces the red Mojang splash with the RelapsedMC one: near-black background, glowing logo, and the logo's own
 * gradient bar under RELAPSED is the loading bar (it fills cyan -> pink as the game loads).
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
	@Shadow
	private float currentProgress;

	/** Where the bar sits in splash_logo.png (1024x384), from tools/make_logo.py. */
	private static final float BAR_X1 = 39.1f, BAR_X2 = 862.3f, BAR_Y1 = 249.5f, BAR_Y2 = 260.9f;

	@Inject(method = "registerTextures", at = @At("TAIL"))
	private static void factionsclient$registerLogo(TextureManager textureManager, CallbackInfo ci) {
		textureManager.registerAndLoad(SplashLogoTexture.ID, new SplashLogoTexture());
	}

	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"))
	private int factionsclient$background(IntSupplier supplier) {
		return 0xFF0B0716;
	}

	/** First half of the Mojang logo -> our whole logo. */
	@Redirect(method = "render", at = @At(value = "INVOKE", ordinal = 0,
			target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
	private void factionsclient$logo(GuiGraphics g, RenderPipeline p, Identifier id, int x, int y, float u, float v,
			int w, int h, int rw, int rh, int tw, int th, int color) {
		int cx = x + w;               // vanilla draws two halves around the center
		int cy = y + h / 2;
		int lh = (int) (h * 1.5f);
		int lw = lh * 1024 / 384;
		float a = ((color >>> 24) & 0xFF) / 255f;
		int lx = cx - lw / 2, ly = cy - lh / 2;
		g.blit(RenderPipelines.GUI_TEXTURED, SplashLogoTexture.ID, lx, ly, 0f, 0f, lw, lh, 1024, 384, 1024, 384, Theme.white(a));
		factionsclient$drawBar(g, lx, ly, lw, lh, a);
	}

	/** The logo's bar, as the progress bar: dim track, gradient fill, soft glow. */
	private void factionsclient$drawBar(GuiGraphics g, int lx, int ly, int lw, int lh, float a) {
		int x1 = lx + Math.round(BAR_X1 * lw / 1024f);
		int x2 = lx + Math.round(BAR_X2 * lw / 1024f);
		int y1 = ly + Math.round(BAR_Y1 * lh / 384f);
		int y2 = Math.max(y1 + 2, ly + Math.round(BAR_Y2 * lh / 384f));
		int w = x2 - x1;
		// rounded track, like the bar in the logo
		g.fill(x1 + 1, y1, x2 - 1, y2, Theme.alpha(0x26FFFFFF, a));
		g.fill(x1, y1 + 1, x2, y2 - 1, Theme.alpha(0x26FFFFFF, a));
		int fw = Math.round(w * Math.max(0f, Math.min(1f, this.currentProgress)));
		if (fw <= 0) return;
		int steps = Math.max(1, fw / 3);
		for (int i = 0; i < steps; i++) {
			int sx = x1 + i * fw / steps;
			int ex = x1 + (i + 1) * fw / steps;
			int c = Theme.gradientAt((float) (sx - x1) / Math.max(1, w));
			g.fill(sx, y1 - 1, ex, y2 + 1, Theme.alpha((c & 0x00FFFFFF) | 0x40000000, a)); // glow
			boolean end = i == 0;
			g.fill(end ? sx + 1 : sx, y1, ex, y2, Theme.alpha(c, a));
			if (end) g.fill(sx, y1 + 1, sx + 1, y2 - 1, Theme.alpha(c, a));
		}
		// bright leading edge
		g.fill(x1 + fw - 1, y1 - 1, x1 + fw, y2 + 1, Theme.alpha(0xFFFFFFFF, a * 0.85f));
	}

	/** Second half of the Mojang logo -> nothing. */
	@Redirect(method = "render", at = @At(value = "INVOKE", ordinal = 1,
			target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
	private void factionsclient$noSecondHalf(GuiGraphics g, RenderPipeline p, Identifier id, int x, int y, float u, float v,
			int w, int h, int rw, int rh, int tw, int th, int color) {
	}

	/** Vanilla's separate bar under the logo -> nothing (the logo's bar shows the progress). */
	@Inject(method = "drawProgressBar", at = @At("HEAD"), cancellable = true)
	private void factionsclient$bar(GuiGraphics g, int x1, int y1, int x2, int y2, float alpha, CallbackInfo ci) {
		ci.cancel();
	}
}
