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

/** Replaces the red Mojang splash with the laG one: near-black background, glowing logo, gradient bar. */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
	@Shadow
	private float currentProgress;

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
		g.blit(RenderPipelines.GUI_TEXTURED, SplashLogoTexture.ID, cx - lw / 2, cy - lh / 2, 0f, 0f, lw, lh, 1024, 384, 1024, 384, Theme.white(a));
	}

	/** Second half of the Mojang logo -> nothing. */
	@Redirect(method = "render", at = @At(value = "INVOKE", ordinal = 1,
			target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
	private void factionsclient$noSecondHalf(GuiGraphics g, RenderPipeline p, Identifier id, int x, int y, float u, float v,
			int w, int h, int rw, int rh, int tw, int th, int color) {
	}

	@Inject(method = "drawProgressBar", at = @At("HEAD"), cancellable = true)
	private void factionsclient$bar(GuiGraphics g, int x1, int y1, int x2, int y2, float alpha, CallbackInfo ci) {
		ci.cancel();
		int w = x2 - x1;
		int h = 4;
		int y = (y1 + y2) / 2 - h / 2;
		g.fill(x1, y, x2, y + h, Theme.alpha(0x30FFFFFF, alpha));
		int fw = Math.max(0, (int) (w * this.currentProgress));
		// The gradient texture may not be loaded yet during the first load, so draw the gradient with fills.
		int steps = Math.max(1, fw / 4);
		for (int i = 0; i < steps; i++) {
			int sx = x1 + i * fw / steps;
			int ex = x1 + (i + 1) * fw / steps;
			g.fill(sx, y, ex, y + h, Theme.alpha(Theme.gradientAt((float) (sx - x1) / Math.max(1, w)), alpha));
		}
	}
}
