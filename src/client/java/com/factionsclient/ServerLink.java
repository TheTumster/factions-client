package com.factionsclient;

import java.util.ArrayList;
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

	/** Teammates on this server, as last sent by the server. */
	public static volatile List<Mate> MATES = List.of();
	/** True once the server has sent us anything this session (i.e. we're on RelapsedMC). */
	public static volatile boolean connected = false;
	private static volatile long lastMates = 0;

	private ServerLink() { }

	public static void init() {
		PayloadTypeRegistry.playS2C().register(MatesPayload.TYPE, MatesPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CrumbsPayload.TYPE, CrumbsPayload.CODEC);

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
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> reset());
	}

	/** Drop stale teammates if the server stops sending (left factions, switched servers, etc.). */
	public static void tick(Minecraft mc) {
		if (!MATES.isEmpty() && System.currentTimeMillis() - lastMates > 5000) MATES = List.of();
	}

	private static void reset() {
		MATES = List.of();
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
