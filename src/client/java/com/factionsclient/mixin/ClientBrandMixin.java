package com.factionsclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.ClientBrandRetriever;

/**
 * The client brand sent to servers reads "fabric+relapsed", so RelapsedMC servers can tell this client has the
 * RelapsedMC pack built in (menus with art, no pack prompt) even when channel adverts don't make it through the proxy.
 */
@Mixin(ClientBrandRetriever.class)
public class ClientBrandMixin {
	@Inject(method = "getClientModName", at = @At("RETURN"), cancellable = true, remap = false)
	private static void relapsed$brand(CallbackInfoReturnable<String> cir) {
		String b = cir.getReturnValue();
		if (b == null || b.isEmpty()) b = "vanilla";
		if (!b.contains("relapsed")) cir.setReturnValue(b + "+relapsed");
	}
}
