package com.factionsclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;

import com.factionsclient.Freelook;

/** Freelook: while held, the camera uses its own rotation instead of the player's. */
@Mixin(Camera.class)
public abstract class CameraFreelookMixin {
	@Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
	private float factionsclient$yaw(Entity entity, float partial) {
		return Freelook.active() ? Freelook.yaw : entity.getViewYRot(partial);
	}

	@Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
	private float factionsclient$pitch(Entity entity, float partial) {
		return Freelook.active() ? Freelook.pitch : entity.getViewXRot(partial);
	}
}
