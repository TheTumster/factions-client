package com.factionsclient.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;

/**
 * RelapsedMC's own resource pack is accepted without a prompt, whatever address the player joined on
 * (play.relapsedmc.com, localhost, a tunnel...). It's recognised by the pack itself: RelapsedMC-pack.zip.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ResourcePackAutoAcceptMixin {
	@Shadow @Final @Nullable protected ServerData serverData;

	@Inject(method = "handleResourcePackPush", at = @At("HEAD"))
	private void relapsed$autoAccept(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
		String url = packet.url();
		if (serverData != null && url != null && url.contains("RelapsedMC-pack")) {
			serverData.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
		}
	}
}
