package com.factionsclient;

import java.util.Locale;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.server.network.EventLoopGroupHolder;

/** play.relapsedmc.com: the pinned server-list entry, the title screen's live status, and one-click join. */
public final class RelapsedServer {
	public static final String IP = "play.relapsedmc.com";
	public static final String NAME = "RelapsedMC";
	private static final long REFRESH_MS = 30_000;

	private static final ServerStatusPinger PINGER = new ServerStatusPinger();
	private static ServerData status;
	private static long lastPing = 0;
	private static volatile boolean pinging = false;

	public enum State { UNKNOWN, PINGING, ONLINE, OFFLINE }

	private RelapsedServer() { }

	public static boolean isRelapsed(String ip) {
		if (ip == null) return false;
		String s = ip.trim().toLowerCase(Locale.ROOT);
		if (s.endsWith(":25565")) s = s.substring(0, s.length() - 6);
		if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
		return s.equals(IP) || s.equals("relapsedmc.com");
	}

	public static ServerData newEntry() {
		return new ServerData(NAME, IP, ServerData.Type.OTHER);
	}

	/** Call every frame from screens that show the status. Re-pings every 30 seconds. */
	public static void tick() {
		PINGER.tick();
		long now = System.currentTimeMillis();
		if (pinging || now - lastPing < REFRESH_MS) return;
		lastPing = now;
		pinging = true;
		ServerData sd = newEntry();
		Minecraft mc = Minecraft.getInstance();
		Thread t = new Thread(() -> {
			try {
				PINGER.pingServer(sd, () -> { }, () -> mc.execute(() -> {
					sd.setState(sd.protocol == SharedConstants.getCurrentVersion().protocolVersion()
							? ServerData.State.SUCCESSFUL : ServerData.State.INCOMPATIBLE);
					status = sd;
					pinging = false;
				}), EventLoopGroupHolder.remote(mc.options.useNativeTransport()));
			} catch (Exception e) {
				mc.execute(() -> {
					sd.setState(ServerData.State.UNREACHABLE);
					status = sd;
					pinging = false;
				});
			}
			// If the ping never answers, let the next refresh try again.
			try {
				Thread.sleep(10_000);
			} catch (InterruptedException ignored) { }
			if (pinging) mc.execute(() -> {
				if (pinging) {
					sd.setState(ServerData.State.UNREACHABLE);
					status = sd;
					pinging = false;
				}
			});
		}, "RelapsedMC-ping");
		t.setDaemon(true);
		t.start();
	}

	/** Stop open ping connections (call when the title screen closes). */
	public static void stop() {
		PINGER.removeAll();
		pinging = false;
		lastPing = 0;
	}

	public static State state() {
		if (status == null) return pinging ? State.PINGING : State.UNKNOWN;
		return switch (status.state()) {
			case SUCCESSFUL, INCOMPATIBLE -> State.ONLINE;
			case UNREACHABLE -> State.OFFLINE;
			default -> State.PINGING;
		};
	}

	/** "42 online", or null if unknown. */
	public static String onlineText() {
		if (status == null || status.players == null || state() != State.ONLINE) return null;
		return status.players.online() + " online";
	}

	public static long ping() {
		return status == null ? -1 : status.ping;
	}

	/** Connect straight to RelapsedMC from any screen. */
	public static void join(Screen from) {
		Minecraft mc = Minecraft.getInstance();
		ServerData sd = null;
		var list = new net.minecraft.client.multiplayer.ServerList(mc);
		list.load(); // also pins it (ServerListMixin)
		for (int i = 0; i < list.size(); i++) {
			if (isRelapsed(list.get(i).ip)) {
				sd = list.get(i);
				break;
			}
		}
		if (sd == null) sd = newEntry();
		stop();
		ConnectScreen.startConnecting(from, mc, ServerAddress.parseString(sd.ip), sd, false, null);
	}
}
