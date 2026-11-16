package com.factionsclient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Faction data straight from the RelapsedMC server (laGCannons' ClientLink).
 * The server decides who is on your faction, so nobody can see another faction's coords,
 * and there are no keys in the pack. On any other server nothing arrives, so team features stay off.
 *
 * Wire format (must match ClientLink.java in lag-network):
 *  relapsed:mates  -> varint n, then n x (utf name, utf dim, double x, double y, double z)
 *  relapsed:crumbs -> utf dim, varint n, then n x (int x, int y, int z)
 *  relapsed:claims -> utf dim, varint radius, varint nTags, nTags x utf, varint n, n x (int cx, int cz, byte rel, varint tag)
 *  relapsed:home   -> bool has, then utf dim, double x, double y, double z
 *  relapsed:realm  -> utf realm (hub, factions, skyblock, smp, cannons): laGCore sends it when you arrive
 */
public final class ServerLink {
	public record Mate(String name, double x, double y, double z, String dim) { }

	public record MatesPayload(List<Mate> mates) implements CustomPacketPayload {
		public static final Type<MatesPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("relapsed", "mates"));
		public static final StreamCodec<RegistryFriendlyByteBuf, MatesPayload> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.mates.size());
					for (Mate m : p.mates) {
						buf.writeUtf(m.name);
						buf.writeUtf(m.dim);
						buf.writeDouble(m.x);
						buf.writeDouble(m.y);
						buf.writeDouble(m.z);
					}
				},
				buf -> {
					int n = Math.min(buf.readVarInt(), 200);
					List<Mate> list = new ArrayList<>(n);
					for (int i = 0; i < n; i++) {
						String name = buf.readUtf(64);
						String dim = buf.readUtf(128);
						list.add(new Mate(name, buf.readDouble(), buf.readDouble(), buf.readDouble(), dim));
					}
					return new MatesPayload(List.copyOf(list));
				});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record CrumbsPayload(String dim, int[] xyz) implements CustomPacketPayload {
		public static final Type<CrumbsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("relapsed", "crumbs"));
		public static final StreamCodec<RegistryFriendlyByteBuf, CrumbsPayload> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeUtf(p.dim);
					buf.writeVarInt(p.xyz.length / 3);
					for (int v : p.xyz) buf.writeInt(v);
				},
				buf -> {
					String dim = buf.readUtf(128);
					int n = Math.min(buf.readVarInt(), 2000);
					int[] xyz = new int[n * 3];
					for (int i = 0; i < xyz.length; i++) xyz[i] = buf.readInt();
					return new CrumbsPayload(dim, xyz);
				});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Relation codes sent by the server. */
	public static final int OWN = 0, ALLY = 1, TRUCE = 2, NEUTRAL = 3, ENEMY = 4, SAFEZONE = 5, WARZONE = 6;

	public record Claim(int rel, String tag) { }

	public record ClaimsPayload(String dim, int radius, Map<Long, Claim> claims) implements CustomPacketPayload {
		public static final Type<ClaimsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("relapsed", "claims"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ClaimsPayload> CODEC = StreamCodec.of(
				(buf, p) -> {
					List<String> tags = new ArrayList<>();
					for (Claim c : p.claims.values()) if (!tags.contains(c.tag())) tags.add(c.tag());
					buf.writeUtf(p.dim);
					buf.writeVarInt(p.radius);
					buf.writeVarInt(tags.size());
					for (String t : tags) buf.writeUtf(t);
					buf.writeVarInt(p.claims.size());
					for (var e : p.claims.entrySet()) {
						buf.writeInt(chunkX(e.getKey()));
						buf.writeInt(chunkZ(e.getKey()));
						buf.writeByte(e.getValue().rel());
						buf.writeVarInt(tags.indexOf(e.getValue().tag()));
					}
				},
				buf -> {
					String dim = buf.readUtf(128);
					int radius = Math.min(buf.readVarInt(), 32);
					int nt = Math.min(buf.readVarInt(), 1024);
					String[] tags = new String[nt];
					for (int i = 0; i < nt; i++) tags[i] = buf.readUtf(64);
					int n = Math.min(buf.readVarInt(), 4096);
					Map<Long, Claim> map = new HashMap<>(n * 2);
					for (int i = 0; i < n; i++) {
						int cx = buf.readInt(), cz = buf.readInt();
						int rel = buf.readByte();
						int ti = buf.readVarInt();
						map.put(chunkKey(cx, cz), new Claim(rel, ti >= 0 && ti < nt ? tags[ti] : "?"));
					}
					return new ClaimsPayload(dim, radius, map);
				});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record Home(String dim, double x, double y, double z) { }

	public record HomePayload(Home home) implements CustomPacketPayload {
		public static final Type<HomePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("relapsed", "home"));
		public static final StreamCodec<RegistryFriendlyByteBuf, HomePayload> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeBoolean(p.home != null);
					if (p.home != null) {
						buf.writeUtf(p.home.dim());
						buf.writeDouble(p.home.x());
						buf.writeDouble(p.home.y());
						buf.writeDouble(p.home.z());
					}
				},
				buf -> buf.readBoolean()
						? new HomePayload(new Home(buf.readUtf(128), buf.readDouble(), buf.readDouble(), buf.readDouble()))
						: new HomePayload(null));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record RealmPayload(String realm) implements CustomPacketPayload {
		public static final Type<RealmPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("relapsed", "realm"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RealmPayload> CODEC = StreamCodec.of(
				(buf, p) -> buf.writeUtf(p.realm), buf -> new RealmPayload(buf.readUtf(64)));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Realm you're on right now ("" if not on RelapsedMC). */
	public static volatile String REALM = "";

	public static long chunkKey(int cx, int cz) {
		return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
	}

	public static int chunkX(long k) {
		return (int) (k >> 32);
	}

	public static int chunkZ(long k) {
		return (int) k;
	}

	/** Claimed chunks around you (wilderness is absent), for CLAIMS_DIM. */
	public static volatile Map<Long, Claim> CLAIMS = Map.of();
	public static volatile String CLAIMS_DIM = "";
	public static volatile int CLAIMS_RADIUS = 0;
	/** Your faction's /f home, or null. */
	public static volatile Home HOME = null;

	/** Owner of the chunk you're standing in, or null for wilderness / unknown. */
	public static Claim claimHere(Minecraft mc) {
		if (mc.player == null || !CLAIMS_DIM.equals(dimId(mc))) return null;
		return CLAIMS.get(chunkKey(mc.player.getBlockX() >> 4, mc.player.getBlockZ() >> 4));
	}

	/** Teammates on this server, as last sent by the server. */
	public static volatile List<Mate> MATES = List.of();
	/** True once the server has sent us anything this session (i.e. we're on RelapsedMC). */
	public static volatile boolean connected = false;
	private static volatile long lastMates = 0;

	private ServerLink() { }

	public static void init() {
		PayloadTypeRegistry.playS2C().register(MatesPayload.TYPE, MatesPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CrumbsPayload.TYPE, CrumbsPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ClaimsPayload.TYPE, ClaimsPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(HomePayload.TYPE, HomePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(RealmPayload.TYPE, RealmPayload.CODEC);

		ClientPlayNetworking.registerGlobalReceiver(RealmPayload.TYPE, (payload, ctx) -> {
			connected = true;
			boolean changed = !payload.realm().equalsIgnoreCase(REALM);
			REALM = payload.realm();
			// Switching realms through the proxy keeps the same connection: clear the last realm's data.
			if (changed) {
				MATES = List.of();
				CLAIMS = Map.of();
				CLAIMS_DIM = "";
				HOME = null;
			}
			Modes.onRealm(payload.realm());
		});

		ClientPlayNetworking.registerGlobalReceiver(ClaimsPayload.TYPE, (payload, ctx) -> {
			connected = true;
			CLAIMS = payload.claims();
			CLAIMS_DIM = payload.dim();
			CLAIMS_RADIUS = payload.radius();
		});

		ClientPlayNetworking.registerGlobalReceiver(HomePayload.TYPE, (payload, ctx) -> {
			connected = true;
			HOME = payload.home();
		});

		ClientPlayNetworking.registerGlobalReceiver(MatesPayload.TYPE, (payload, ctx) -> {
			connected = true;
			lastMates = System.currentTimeMillis();
			MATES = FactionsClient.CONFIG.team ? payload.mates() : List.of();
		});

		ClientPlayNetworking.registerGlobalReceiver(CrumbsPayload.TYPE, (payload, ctx) -> {
			connected = true;
			Minecraft mc = Minecraft.getInstance();
			if (!FactionsClient.CONFIG.shareCrumbs || !payload.dim().equals(dimId(mc))) return;
			long now = System.currentTimeMillis();
			int[] a = payload.xyz();
			for (int i = 0; i + 2 < a.length; i += 3) {
				// You already see TNT near you yourself (orange); only add hits from further away.
				if (mc.player != null && mc.player.distanceToSqr(a[i] + 0.5, a[i + 1] + 0.5, a[i + 2] + 0.5) < 64 * 64) continue;
				Crumbs.MARKERS.add(new Crumbs.Marker(new Vec3(a[i] + 0.5, a[i + 1] + 0.5, a[i + 2] + 0.5), now, true, "claim"));
			}
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> reset());
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
			reset();
			var sd = mc.getCurrentServer();
			if (sd == null || !RelapsedServer.isRelapsed(sd.ip)) Modes.onOtherServer();
		});
	}

	/** Smoke test only: pretend the server just sent teammates so tick() doesn't clear them. */
	static void lastMatesForSmoke() {
		lastMates = System.currentTimeMillis() + 600_000;
	}

	/** Drop stale teammates if the server stops sending (left factions, switched servers, etc.). */
	public static void tick(Minecraft mc) {
		if (!MATES.isEmpty() && System.currentTimeMillis() - lastMates > 5000) MATES = List.of();
	}

	private static void reset() {
		MATES = List.of();
		CLAIMS = Map.of();
		CLAIMS_DIM = "";
		HOME = null;
		REALM = "";
		connected = false;
	}

	public static String dimId(Minecraft mc) {
		return mc.level == null ? "" : mc.level.dimension().identifier().toString();
	}

	/** Short status line for the menu/HUD. */
	public static String status() {
		return connected ? "Connected to RelapsedMC." : "Team features work on RelapsedMC only.";
	}
}
