package com.factionsclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;

import com.factionsclient.Freelook;

/** Freelook: mouse turns the camera, not you. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerFreelookMixin {
	@Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void factionsclient$turn(LocalPlayer player, double dx, double dy) {
		if (Freelook.active()) Freelook.turn(dx, dy);
		else player.turn(dx, dy);
	}
}
