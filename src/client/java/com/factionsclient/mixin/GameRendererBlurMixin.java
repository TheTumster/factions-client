package com.factionsclient.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;

import com.factionsclient.FactionsClient;

/** Motion blur: right after the world is drawn (before the HUD), blend in the previous frame. */
@Mixin(GameRenderer.class)
public abstract class GameRendererBlurMixin {
	@Shadow @Final private Minecraft minecraft;
	@Shadow @Final private CrossFrameResourcePool resourcePool;

	@Inject(method = "render", at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/renderer/GameRenderer;postEffectId:Lnet/minecraft/resources/Identifier;",
			opcode = Opcodes.GETFIELD, ordinal = 0))
	private void factionsclient$motionBlur(DeltaTracker delta, boolean renderLevel, CallbackInfo ci) {
		var cfg = FactionsClient.CONFIG;
		if (cfg == null || !cfg.motionBlur || minecraft.level == null) return;
		int lvl = Math.max(1, Math.min(10, cfg.motionBlurStrength));
		PostChain chain = minecraft.getShaderManager().getPostChain(
				Identifier.fromNamespaceAndPath("factionsclient", "motion_blur_" + lvl), LevelTargetBundle.MAIN_TARGETS);
		if (chain != null) chain.process(minecraft.getMainRenderTarget(), resourcePool);
	}
}
