package com.factionsclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;

import com.factionsclient.FactionsClient;

/** Factions Visuals: see through water and lava (no fog wall). */
public final class FogEnvironmentMixins {
	private FogEnvironmentMixins() { }

	@Mixin(WaterFogEnvironment.class)
	public abstract static class Water {
		@Inject(method = "setupFog", at = @At("TAIL"))
		private void factionsclient$clearWater(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker dt, CallbackInfo ci) {
			var c = FactionsClient.CONFIG;
			if (c != null && c.clearWater) com.factionsclient.FactionsVisuals.clearFog(fog, renderDistance, c.clearWaterRange);
		}
	}

	@Mixin(LavaFogEnvironment.class)
	public abstract static class Lava {
		@Inject(method = "setupFog", at = @At("TAIL"))
		private void factionsclient$clearLava(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker dt, CallbackInfo ci) {
			var c = FactionsClient.CONFIG;
			if (c != null && c.clearLava) com.factionsclient.FactionsVisuals.clearFog(fog, renderDistance, 40);
		}
	}
}
