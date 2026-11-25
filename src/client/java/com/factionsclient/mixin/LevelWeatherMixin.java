package com.factionsclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;

import com.factionsclient.FactionsClient;

/** No Weather: rain and thunder off on your screen. */
@Mixin(Level.class)
public abstract class LevelWeatherMixin {
	@Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
	private void factionsclient$rain(float partial, CallbackInfoReturnable<Float> cir) {
		var c = FactionsClient.CONFIG;
		if (c != null && c.noWeather && (Object) this instanceof ClientLevel) cir.setReturnValue(0f);
	}

	@Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
	private void factionsclient$thunder(float partial, CallbackInfoReturnable<Float> cir) {
		var c = FactionsClient.CONFIG;
		if (c != null && c.noWeather && (Object) this instanceof ClientLevel) cir.setReturnValue(0f);
	}
}
