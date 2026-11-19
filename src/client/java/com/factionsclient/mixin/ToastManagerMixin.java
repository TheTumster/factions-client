package com.factionsclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;

/** Drops the "Chat messages can't be verified" toast. RelapsedMC runs behind a proxy without chat signing, so it
 *  shows on every join and means nothing to players. */
@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {
	@Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
	private void factionsclient$hideUnsecureChat(Toast toast, CallbackInfo ci) {
		if (toast instanceof SystemToast && toast.getToken() == SystemToast.SystemToastId.UNSECURE_SERVER_WARNING) ci.cancel();
	}
}
