package com.factionsclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.multiplayer.ClientLevel;

import com.factionsclient.FactionsClient;

/** Time Changer: pick your own time of day (only you see it). */
@Mixin(ClientLevel.ClientLevelData.class)
public abstract class ClientLevelDataTimeMixin {
	@Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
	private void factionsclient$time(CallbackInfoReturnable<Long> cir) {
		var c = FactionsClient.CONFIG;
		if (c != null && c.timeChanger) cir.setReturnValue((long) c.timeOfDay * 1000L);
	}
}
