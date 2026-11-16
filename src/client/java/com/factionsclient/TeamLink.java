package com.factionsclient;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * RelapsedMC Teams on the client (server side: lag-network laGCore Teams.java; wire formats must match).
 * Team actions are server commands (/team ..., /tc), so they also work for players without the client.
 */
public final class TeamLink {
	public record Member(String name, boolean online, String realm) { }

	public record Invite(String team, String by) { }

	public record Schem(int id, String name, String by, int size, long time) { }

	public record Ping(String from, String kind, String dim, double x, double y, double z, long at) { }

	// ---------------------------------------------------------------- payloads

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("relapsed", path);
	}

	public record StatePayload(String team, String leader, boolean youLead, List<Member> members, List<Invite> invites,
			List<Schem> schems) implements CustomPacketPayload {
		public static final Type<StatePayload> TYPE = new Type<>(id("team"));
		public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> CODEC = StreamCodec.of((buf, p) -> {
			throw new UnsupportedOperationException("server-only");
		}, buf -> {
			String team = buf.readUtf(64), leader = buf.readUtf(64);
			boolean lead = buf.readBoolean();
			List<Member> ms = new ArrayList<>();
			if (!team.isEmpty()) {
				int n = Math.min(buf.readVarInt(), 64);
				for (int i = 0; i < n; i++) ms.add(new Member(buf.readUtf(64), buf.readBoolean(), buf.readUtf(64)));
			} else {
				buf.readVarInt(); // always 0
			}
			List<Invite> inv = new ArrayList<>();
			int ni = Math.min(buf.readVarInt(), 64);
			for (int i = 0; i < ni; i++) inv.add(new Invite(buf.readUtf(64), buf.readUtf(64)));
			List<Schem> sc = new ArrayList<>();
			int ns = Math.min(buf.readVarInt(), 200);
			for (int i = 0; i < ns; i++) sc.add(new Schem(buf.readVarInt(), buf.readUtf(128), buf.readUtf(64), buf.readVarInt(), buf.readLong()));
			return new StatePayload(team, leader, lead, ms, inv, sc);
		});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record MatesPayload(List<ServerLink.Mate> mates) implements CustomPacketPayload {
		public static final Type<MatesPayload> TYPE = new Type<>(id("tmates"));
		public static final StreamCodec<RegistryFriendlyByteBuf, MatesPayload> CODEC = StreamCodec.of((buf, p) -> {
			throw new UnsupportedOperationException("server-only");
		}, buf -> {
			int n = Math.min(buf.readVarInt(), 64);
			List<ServerLink.Mate> l = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				String name = buf.readUtf(64), dim = buf.readUtf(128);
				l.add(new ServerLink.Mate(name, buf.readDouble(), buf.readDouble(), buf.readDouble(), dim));
			}
			return new MatesPayload(List.copyOf(l));
		});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record PingPayload(Ping ping) implements CustomPacketPayload {
		public static final Type<PingPayload> TYPE = new Type<>(id("ping"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PingPayload> CODEC = StreamCodec.of((buf, p) -> {
			throw new UnsupportedOperationException("server-only");
		}, buf -> new PingPayload(new Ping(buf.readUtf(64), buf.readUtf(16), buf.readUtf(128),
				buf.readDouble(), buf.readDouble(), buf.readDouble(), System.currentTimeMillis())));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record SchemDataPayload(int id, String name, int total, int index, byte[] data) implements CustomPacketPayload {
		public static final Type<SchemDataPayload> TYPE = new Type<>(id("schem_data"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SchemDataPayload> CODEC = StreamCodec.of((buf, p) -> {
			throw new UnsupportedOperationException("server-only");
		}, buf -> {
			int sid = buf.readVarInt();
			String name = buf.readUtf(128);
			int total = buf.readVarInt(), index = buf.readVarInt(), len = buf.readVarInt();
			if (len < 0 || len > 64_000) throw new IllegalArgumentException("bad chunk");
			byte[] d = new byte[len];
			buf.readBytes(d);
			return new SchemDataPayload(sid, name, total, index, d);
		});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record SchemUpPayload(String name, int total, int index, byte[] data) implements CustomPacketPayload {
		public static final Type<SchemUpPayload> TYPE = new Type<>(id("schem_up"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SchemUpPayload> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeUtf(p.name);
			buf.writeVarInt(p.total);
			buf.writeVarInt(p.index);
			buf.writeVarInt(p.data.length);
			buf.writeBytes(p.data);
		}, buf -> {
			throw new UnsupportedOperationException("client-only");
		});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record SchemIdPayload(Type<SchemIdPayload> kind, int id) implements CustomPacketPayload {
		public static final Type<SchemIdPayload> GET = new Type<>(id("schem_get"));
		public static final Type<SchemIdPayload> DEL = new Type<>(id("schem_del"));

		static StreamCodec<RegistryFriendlyByteBuf, SchemIdPayload> codec(Type<SchemIdPayload> t) {
			return StreamCodec.of((buf, p) -> buf.writeVarInt(p.id), buf -> new SchemIdPayload(t, buf.readVarInt()));
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return kind;
		}
	}

	// ---------------------------------------------------------------- state

	public static volatile boolean connected = false;
	public static volatile String team = "";
	public static volatile String leader = "";
	public static volatile boolean youLead = false;
	public static volatile List<Member> members = List.of();
	public static volatile List<Invite> invites = List.of();
	public static volatile List<Schem> schems = List.of();
	/** Teammates on this server (not faction mates: those are ServerLink.MATES). */
	public static volatile List<ServerLink.Mate> MATES = List.of();
	public static final List<Ping> PINGS = new java.util.concurrent.CopyOnWriteArrayList<>();
	private static volatile long lastMates = 0;

	/** Download in progress. */
	private static int dlId = -1;
	private static byte[][] dlParts;
	private static int dlGot;
	public static volatile String status = "";

	private TeamLink() { }

	public static boolean inTeam() {
		return !team.isEmpty();
	}

	public static void init() {
		PayloadTypeRegistry.playS2C().register(StatePayload.TYPE, StatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(MatesPayload.TYPE, MatesPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PingPayload.TYPE, PingPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SchemDataPayload.TYPE, SchemDataPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SchemUpPayload.TYPE, SchemUpPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SchemIdPayload.GET, SchemIdPayload.codec(SchemIdPayload.GET));
		PayloadTypeRegistry.playC2S().register(SchemIdPayload.DEL, SchemIdPayload.codec(SchemIdPayload.DEL));

		ClientPlayNetworking.registerGlobalReceiver(StatePayload.TYPE, (p, ctx) -> {
			connected = true;
			team = p.team();
			leader = p.leader();
			youLead = p.youLead();
			members = List.copyOf(p.members());
			invites = List.copyOf(p.invites());
			schems = List.copyOf(p.schems());
		});
		ClientPlayNetworking.registerGlobalReceiver(MatesPayload.TYPE, (p, ctx) -> {
			connected = true;
			lastMates = System.currentTimeMillis();
			MATES = FactionsClient.CONFIG.teamTrack ? p.mates() : List.of();
		});
		ClientPlayNetworking.registerGlobalReceiver(PingPayload.TYPE, (p, ctx) -> {
			if (!FactionsClient.CONFIG.teamPings) return;
			PINGS.removeIf(o -> o.from().equals(p.ping().from())); // one ping per person
			PINGS.add(p.ping());
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
					p.ping().kind().equals("enemy") ? SoundEvents.NOTE_BLOCK_BELL.value() : SoundEvents.NOTE_BLOCK_CHIME.value(), 1.3f, 0.7f));
		});
		ClientPlayNetworking.registerGlobalReceiver(SchemDataPayload.TYPE, (p, ctx) -> chunk(p));

		ClientPlayConnectionEvents.JOIN.register((h, s, mc) -> reset());
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> reset());
	}

	private static void reset() {
		connected = false;
		team = leader = "";
		youLead = false;
		members = List.of();
		invites = List.of();
		schems = List.of();
		MATES = List.of();
		PINGS.clear();
		dlId = -1;
	}

	/** Smoke test only: keep the fake teammates. */
	static void lastMatesForSmoke() {
		lastMates = System.currentTimeMillis() + 600_000;
	}

	public static void tick(Minecraft mc) {
		if (!MATES.isEmpty() && System.currentTimeMillis() - lastMates > 5000) MATES = List.of();
		PINGS.removeIf(p -> System.currentTimeMillis() - p.at() > 12_000);
		if (FactionsClient.pingKey != null) {
			while (FactionsClient.pingKey.consumeClick()) ping(mc);
		}
	}

	// ---------------------------------------------------------------- actions

	public static void cmd(String command) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.connection.sendCommand(command);
	}

	/** Ping where you're looking (Shift = enemy). */
	public static void ping(Minecraft mc) {
		if (mc.player == null || !inTeam() || !FactionsClient.CONFIG.teamPings) return;
		HitResult hit = mc.player.pick(256, 0, false);
		Vec3 at = hit.getType() == HitResult.Type.BLOCK ? Vec3.atCenterOf(((BlockHitResult) hit).getBlockPos())
				: mc.player.getEyePosition().add(mc.player.getLookAngle().scale(64));
		String kind = mc.player.isShiftKeyDown() ? "enemy" : "here";
		cmd(String.format(Locale.ROOT, "team ping %.1f %.1f %.1f %s", at.x, at.y, at.z, kind));
	}

	public static void download(Schem s) {
		dlId = s.id();
		dlParts = null;
		dlGot = 0;
		status = "Downloading " + s.name() + "…";
		ClientPlayNetworking.send(new SchemIdPayload(SchemIdPayload.GET, s.id()));
	}

	public static void delete(Schem s) {
		ClientPlayNetworking.send(new SchemIdPayload(SchemIdPayload.DEL, s.id()));
	}

	private static void chunk(SchemDataPayload p) {
		if (p.id() != dlId || p.total() <= 0 || p.total() > 200 || p.index() >= p.total()) return;
		if (dlParts == null || dlParts.length != p.total()) {
			dlParts = new byte[p.total()][];
			dlGot = 0;
		}
		if (dlParts[p.index()] == null) {
			dlParts[p.index()] = p.data();
			dlGot++;
		}
		status = "Downloading " + p.name() + "… " + (dlGot * 100 / p.total()) + "%";
		if (dlGot < p.total()) return;
		ByteArrayOutputStream all = new ByteArrayOutputStream();
		for (byte[] b : dlParts) all.writeBytes(b);
		dlId = -1;
		dlParts = null;
		try {
			String safe = p.name().replaceAll("[^A-Za-z0-9 _.\\-]", "_");
			Path dir = SchemShare.dir();
			Files.createDirectories(dir);
			Path out = dir.resolve(safe);
			for (int n = 2; Files.exists(out); n++) {
				int dot = safe.lastIndexOf('.');
				out = dir.resolve((dot > 0 ? safe.substring(0, dot) : safe) + " (" + n + ")" + (dot > 0 ? safe.substring(dot) : ""));
			}
			Files.write(out, all.toByteArray());
			status = "Saved " + out.getFileName() + " to your schematics folder";
			SchemShare.msg("Downloaded " + out.getFileName() + ": load it in Litematica", ChatFormatting.GREEN);
		} catch (Exception e) {
			status = "Couldn't save: " + e.getMessage();
		}
	}

	/** Send a schematic from your schematics folder to the team (30 KB chunks). */
	public static void upload(String fileName) {
		if (!inTeam()) {
			status = "Join a team first";
			return;
		}
		try {
			Path f = SchemShare.dir().resolve(fileName).normalize();
			if (!f.startsWith(SchemShare.dir().normalize()) || !Files.isRegularFile(f)) {
				status = "File not found";
				return;
			}
			byte[] data = Files.readAllBytes(f);
			if (data.length > 4 * 1024 * 1024) {
				status = "Too big (max 4 MB)";
				return;
			}
			int chunk = 30_000, total = Math.max(1, (data.length + chunk - 1) / chunk);
			for (int i = 0; i < total; i++) {
				int from = i * chunk, len = Math.min(chunk, data.length - from);
				byte[] part = len <= 0 ? new byte[0] : java.util.Arrays.copyOfRange(data, from, from + len);
				ClientPlayNetworking.send(new SchemUpPayload(f.getFileName().toString(), total, i, part));
			}
			status = "Uploaded " + fileName + " to " + team;
		} catch (Exception e) {
			status = "Upload failed: " + e.getMessage();
		}
	}

	/** Schematic files you could upload (newest first, subfolders included). */
	public static List<String> localFiles() {
		return SchemShare.localFiles();
	}
}
