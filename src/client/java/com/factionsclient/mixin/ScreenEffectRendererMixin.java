package com.factionsclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import com.factionsclient.FactionsClient;

/** Low Fire: the burning overlay sits low (and a bit see-through) instead of covering your screen. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
	@Inject(method = "renderFire", at = @At("HEAD"))
	private static void factionsclient$lowFireStart(PoseStack pose, MultiBufferSource buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
		pose.pushPose();
		var c = FactionsClient.CONFIG;
		if (c != null && c.lowFire) pose.translate(0.0F, -c.lowFireAmount / 100f * 0.45F, 0.0F);
	}

	@Inject(method = "renderFire", at = @At("RETURN"))
	private static void factionsclient$lowFireEnd(PoseStack pose, MultiBufferSource buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
		pose.popPose();
	}

	@ModifyConstant(method = "renderFire", constant = @Constant(floatValue = 0.9F))
	private static float factionsclient$fireAlpha(float alpha) {
		var c = FactionsClient.CONFIG;
		return c != null && c.lowFire ? 0.55F : alpha;
	}
}
