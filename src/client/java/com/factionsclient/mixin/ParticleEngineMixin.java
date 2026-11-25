package com.factionsclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

import com.factionsclient.FactionsVisuals;

/** Factions Visuals: per-type particle switches, and a small single puff instead of the huge explosion cloud. */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
	@Shadow
	public abstract Particle createParticle(ParticleOptions options, double x, double y, double z, double dx, double dy, double dz);

	@Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
	private void factionsclient$filter(ParticleOptions options, double x, double y, double z, double dx, double dy, double dz,
			CallbackInfoReturnable<Particle> cir) {
		var type = options.getType();
		if (type == ParticleTypes.EXPLOSION_EMITTER && FactionsVisuals.smallExplosions()) {
			cir.setReturnValue(FactionsVisuals.explosionsOff() ? null : createParticle(ParticleTypes.EXPLOSION, x, y, z, 1.0, 0.0, 0.0));
			return;
		}
		if (FactionsVisuals.blocked(type)) cir.setReturnValue(null);
	}

	/** Block break dust is added as a finished particle, not through createParticle. */
	@Inject(method = "add", at = @At("HEAD"), cancellable = true)
	private void factionsclient$filterBlockDust(Particle particle, CallbackInfo ci) {
		if (particle instanceof TerrainParticle && FactionsVisuals.blockDustOff()) ci.cancel();
	}
}
