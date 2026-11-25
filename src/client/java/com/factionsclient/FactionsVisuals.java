package com.factionsclient;

import java.util.List;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;

import com.factionsclient.ModuleRegistry.BoolS;
import com.factionsclient.ModuleRegistry.IntS;
import com.factionsclient.ModuleRegistry.Setting;

/** Factions Visuals: clear water/lava, low fire, particle switches, hide TNT/sand. */
public final class FactionsVisuals {
	private FactionsVisuals() { }

	private static Config c() {
		return FactionsClient.CONFIG;
	}

	public static void clearFog(net.minecraft.client.renderer.fog.FogData fog, float renderDistance, int percent) {
		float end = Math.max(fog.environmentalEnd, renderDistance * percent / 100f);
		fog.environmentalStart = Math.max(fog.environmentalStart, end * 0.6f);
		fog.environmentalEnd = end;
		fog.skyEnd = Math.max(fog.skyEnd, end);
		fog.cloudEnd = Math.max(fog.cloudEnd, end);
	}

	public static boolean smallExplosions() {
		return c() != null && c().fxExplosions < 2;
	}

	public static boolean explosionsOff() {
		return c() != null && c().fxExplosions == 0;
	}

	public static boolean blockDustOff() {
		return c() != null && !c().fxBlockDust;
	}

	public static boolean blocked(ParticleType<?> t) {
		Config c = c();
		if (c == null) return false;
		if (c.fxExplosions == 0 && (t == ParticleTypes.EXPLOSION || t == ParticleTypes.POOF)) return true;
		if (!c.fxTntSmoke && (t == ParticleTypes.SMOKE || t == ParticleTypes.LARGE_SMOKE)) return true;
		if (!c.fxBlockDust && (t == ParticleTypes.BLOCK || t == ParticleTypes.FALLING_DUST || t == ParticleTypes.DUST_PILLAR
				|| t == ParticleTypes.BLOCK_CRUMBLE)) return true;
		if (!c.fxFire && (t == ParticleTypes.FLAME || t == ParticleTypes.SMALL_FLAME || t == ParticleTypes.SOUL_FIRE_FLAME
				|| t == ParticleTypes.CAMPFIRE_COSY_SMOKE || t == ParticleTypes.CAMPFIRE_SIGNAL_SMOKE)) return true;
		if (!c.fxLava && (t == ParticleTypes.LAVA || t == ParticleTypes.DRIPPING_LAVA || t == ParticleTypes.FALLING_LAVA
				|| t == ParticleTypes.LANDING_LAVA)) return true;
		if (!c.fxDrips && (t == ParticleTypes.DRIPPING_WATER || t == ParticleTypes.FALLING_WATER || t == ParticleTypes.SPLASH
				|| t == ParticleTypes.BUBBLE || t == ParticleTypes.BUBBLE_POP || t == ParticleTypes.UNDERWATER)) return true;
		return false;
	}

	static List<Setting> settings() {
		return List.of(
				new BoolS("Clear water (no underwater fog)", () -> c().clearWater, v -> c().clearWater = v),
				new IntS("Water view distance", 20, 100, 10, "%", () -> c().clearWaterRange, v -> c().clearWaterRange = v),
				new BoolS("Clear lava", () -> c().clearLava, v -> c().clearLava = v),
				new BoolS("Low fire", () -> c().lowFire, v -> c().lowFire = v),
				new IntS("Fire lowered by", 10, 100, 10, "%", () -> c().lowFireAmount, v -> c().lowFireAmount = v),
				new IntS("Explosions (0 off, 1 small puff, 2 vanilla)", 0, 2, 1, "", () -> c().fxExplosions, v -> c().fxExplosions = v),
				new BoolS("TNT fuse smoke", () -> c().fxTntSmoke, v -> c().fxTntSmoke = v),
				new BoolS("Sand + block break dust", () -> c().fxBlockDust, v -> c().fxBlockDust = v),
				new BoolS("Fire + flame particles", () -> c().fxFire, v -> c().fxFire = v),
				new BoolS("Lava pops + drips", () -> c().fxLava, v -> c().fxLava = v),
				new BoolS("Water drips + bubbles", () -> c().fxDrips, v -> c().fxDrips = v),
				new BoolS("Hide TNT entities", () -> c().hideTnt, v -> c().hideTnt = v),
				new BoolS("Hide falling sand", () -> c().hideSand, v -> c().hideSand = v));
	}
}
