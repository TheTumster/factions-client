package com.factionsclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.GameRenderer;

import com.factionsclient.FactionsClient;

/** No Hurt Cam: taking damage doesn't shake your view. */
@Mixin(GameRenderer.class)
public abstract class GameRendererHurtMixin {
	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void factionsclient$noHurtCam(PoseStack pose, float partial, CallbackInfo ci) {
		var c = FactionsClient.CONFIG;
		if (c != null && c.noHurtCam) ci.cancel();
	}
}
