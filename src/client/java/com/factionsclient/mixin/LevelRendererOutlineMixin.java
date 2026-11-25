package com.factionsclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.renderer.LevelRenderer;

import com.factionsclient.FactionsClient;

/** Block Outline: your colour and thickness for the box around the block you're looking at. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererOutlineMixin {
	@ModifyVariable(method = "renderHitOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int factionsclient$outlineColor(int color) {
		var c = FactionsClient.CONFIG;
		return c != null && c.blockOutline ? c.blockOutlineColor : color;
	}

	@ModifyVariable(method = "renderHitOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float factionsclient$outlineWidth(float width) {
		var c = FactionsClient.CONFIG;
		return c != null && c.blockOutline ? Math.max(width, c.blockOutlineWidth) : width;
	}
}
