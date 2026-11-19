package com.factionsclient;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.core.UUIDUtil;

import com.factionsclient.mixin.MinecraftAccountAccessor;

/**
 * Account switcher. Your real (Microsoft) account is always there; offline ("cracked") names can be added for
 * offline-mode servers. Offline names can't join online-mode servers (RelapsedMC included): switch back first.
 */
public final class Accounts {
	public static final Pattern VALID = Pattern.compile("[A-Za-z0-9_]{3,16}");
	private static User main;
	private static ProfileKeyPairManager mainKeys;

	private Accounts() { }

	private static void remember(Minecraft mc) {
		if (main == null) {
			main = mc.getUser();
			mainKeys = ((MinecraftAccountAccessor) mc).factionsclient$getKeys();
		}
	}

	public static String mainName(Minecraft mc) {
		remember(mc);
		return main.getName();
	}

	/** null = your real account. */
	public static String activeOffline() {
		return FactionsClient.CONFIG.activeOfflineAccount;
	}

	public static void useOffline(Minecraft mc, String name) {
		if (!VALID.matcher(name).matches()) return;
		remember(mc);
		UUID id = UUIDUtil.createOfflinePlayerUUID(name);
		User u = new User(name, id, "0", Optional.empty(), Optional.empty());
		((MinecraftAccountAccessor) mc).factionsclient$setUser(u);
		((MinecraftAccountAccessor) mc).factionsclient$setKeys(
				ProfileKeyPairManager.create(com.mojang.authlib.minecraft.UserApiService.OFFLINE, u, mc.gameDirectory.toPath()));
		FactionsClient.CONFIG.activeOfflineAccount = name;
		if (!FactionsClient.CONFIG.offlineAccounts.contains(name)) FactionsClient.CONFIG.offlineAccounts.add(name);
		FactionsClient.CONFIG.save();
		FactionsClient.LOG.info("Switched to offline account {}", name);
	}

	public static void useMain(Minecraft mc) {
		remember(mc);
		((MinecraftAccountAccessor) mc).factionsclient$setUser(main);
		((MinecraftAccountAccessor) mc).factionsclient$setKeys(mainKeys);
		FactionsClient.CONFIG.activeOfflineAccount = null;
		FactionsClient.CONFIG.save();
	}

	public static void remove(Minecraft mc, String name) {
		FactionsClient.CONFIG.offlineAccounts.remove(name);
		if (name.equals(FactionsClient.CONFIG.activeOfflineAccount)) useMain(mc);
		FactionsClient.CONFIG.save();
	}

	/** On launch: go back to the offline name you were last using. */
	public static void restore(Minecraft mc) {
		String n = FactionsClient.CONFIG.activeOfflineAccount;
		if (n != null) useOffline(mc, n);
		else remember(mc);
	}
}
