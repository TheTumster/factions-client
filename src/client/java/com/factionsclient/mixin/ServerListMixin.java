package com.factionsclient.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;

import com.factionsclient.FactionsClient;
import com.factionsclient.RelapsedServer;

/** Every time the server list loads, RelapsedMC is entry #1 (added back if it was deleted, moved back if it was moved). */
@Mixin(ServerList.class)
public abstract class ServerListMixin {
	@Shadow
	@Final
	private List<ServerData> serverList;

	@Shadow
	@Final
	private List<ServerData> hiddenServerList;

	@Inject(method = "load", at = @At("RETURN")) // RETURN, not TAIL: load() returns early when servers.dat doesn't exist yet (new installs)
	private void factionsclient$pinRelapsed(CallbackInfo ci) {
		if (FactionsClient.CONFIG == null || !FactionsClient.CONFIG.pinServer) return;
		ServerData pinned = null;
		for (int i = 0; i < serverList.size(); i++) {
			if (RelapsedServer.isRelapsed(serverList.get(i).ip)) {
				pinned = serverList.remove(i);
				break;
			}
		}
		hiddenServerList.removeIf(s -> RelapsedServer.isRelapsed(s.ip));
		serverList.removeIf(s -> RelapsedServer.isRelapsed(s.ip)); // duplicates
		if (pinned == null) pinned = RelapsedServer.newEntry();
		pinned.name = RelapsedServer.NAME;
		if (pinned.getResourcePackStatus() == ServerData.ServerPackStatus.PROMPT) {
			pinned.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED); // server pack loads without the prompt
		}
		serverList.add(0, pinned);
	}
}
