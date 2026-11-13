package com.factionsclient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Group system. Once a second: push your position, pull your teammates' positions,
 * push new patch crumbs and pull teammates' patch crumbs. Only for people on the same server.
 */
public final class TeamSync {
	public record Mate(String name, double x, double y, double z, String dim) { }

	public static volatile List<Mate> MATES = List.of();
	public static volatile String lastError = null;

	private static final AtomicBoolean BUSY = new AtomicBoolean(false);
	private static int ticks = 0;
	private static String crumbsSince = Instant.now().toString();

	private TeamSync() { }

	public static String serverId(Minecraft mc) {
		ServerData sd = mc.getCurrentServer();
		return sd == null ? "singleplayer" : sd.ip.toLowerCase(Locale.ROOT);
	}

	public static String dimId(Minecraft mc) {
		return mc.level == null ? "" : mc.level.dimension().identifier().toString();
	}

	public static void tick(Minecraft mc) {
		Config cfg = FactionsClient.CONFIG;
		if (mc.player == null || mc.level == null || !cfg.backendReady()) {
			MATES = List.of();
			return;
		}
		if (++ticks < 20) return;
		ticks = 0;
		if (!cfg.team && !cfg.shareCrumbs) return;
		if (!BUSY.compareAndSet(false, true)) return; // previous round still running

		String me = mc.getUser().getName();
		String server = serverId(mc);
		String dim = dimId(mc);
		Vec3 pos = mc.player.position();

		List<BlockPos> outgoing = new ArrayList<>();
		synchronized (Crumbs.OUTBOX) {
			outgoing.addAll(Crumbs.OUTBOX);
			Crumbs.OUTBOX.clear();
		}

		var chain = java.util.concurrent.CompletableFuture.completedFuture((String) null);

		if (cfg.team) {
			JsonObject row = new JsonObject();
			row.addProperty("team", cfg.teamKey);
			row.addProperty("name", me);
			row.addProperty("server", server);
			row.addProperty("dim", dim);
			row.addProperty("x", pos.x);
			row.addProperty("y", pos.y);
			row.addProperty("z", pos.z);
			row.addProperty("updated_at", Instant.now().toString());

			String since = Instant.now().minusSeconds(15).toString();
			String query = "team_positions?select=name,x,y,z,dim"
					+ "&team=eq." + Supa.enc(cfg.teamKey)
					+ "&server=eq." + Supa.enc(server)
					+ "&name=neq." + Supa.enc(me)
					+ "&updated_at=gt." + Supa.enc(since);

			chain = chain
					.thenCompose(v -> Supa.post("team_positions", row.toString(), true))
					.thenCompose(v -> Supa.get(query))
					.thenApply(body -> {
						List<Mate> list = new ArrayList<>();
						for (JsonElement el : JsonParser.parseString(body).getAsJsonArray()) {
							JsonObject o = el.getAsJsonObject();
							list.add(new Mate(o.get("name").getAsString(), o.get("x").getAsDouble(),
									o.get("y").getAsDouble(), o.get("z").getAsDouble(), o.get("dim").getAsString()));
						}
						MATES = List.copyOf(list);
						return null;
					});
		} else {
			MATES = List.of();
		}

		if (cfg.shareCrumbs) {
			if (!outgoing.isEmpty()) {
				JsonArray rows = new JsonArray();
				for (BlockPos p : outgoing.subList(0, Math.min(outgoing.size(), 100))) {
					JsonObject o = new JsonObject();
					o.addProperty("team", cfg.teamKey);
					o.addProperty("server", server);
					o.addProperty("dim", dim);
					o.addProperty("reporter", me);
					o.addProperty("x", p.getX());
					o.addProperty("y", p.getY());
					o.addProperty("z", p.getZ());
					rows.add(o);
				}
				chain = chain.thenCompose(v -> Supa.post("crumbs", rows.toString(), false));
			}

			String since = crumbsSince;
			String query = "crumbs?select=x,y,z,reporter,created_at"
					+ "&team=eq." + Supa.enc(cfg.teamKey)
					+ "&server=eq." + Supa.enc(server)
					+ "&dim=eq." + Supa.enc(dim)
					+ "&reporter=neq." + Supa.enc(me)
					+ "&created_at=gt." + Supa.enc(since)
					+ "&order=created_at.asc&limit=500";

			chain = chain.thenCompose(v -> Supa.get(query)).thenApply(body -> {
				long now = System.currentTimeMillis();
				for (JsonElement el : JsonParser.parseString(body).getAsJsonArray()) {
					JsonObject o = el.getAsJsonObject();
					Vec3 p = new Vec3(o.get("x").getAsInt() + 0.5, o.get("y").getAsInt() + 0.5, o.get("z").getAsInt() + 0.5);
					Crumbs.MARKERS.add(new Crumbs.Marker(p, now, true, o.get("reporter").getAsString()));
					crumbsSince = o.get("created_at").getAsString();
				}
				return null;
			});
		}

		chain.whenComplete((v, err) -> {
			lastError = err == null ? null : rootMessage(err);
			if (err != null) FactionsClient.LOG.warn("Team sync failed: {}", lastError);
			BUSY.set(false);
		});
	}

	private static String rootMessage(Throwable t) {
		while (t.getCause() != null) t = t.getCause();
		String m = t.getMessage();
		return m == null ? t.getClass().getSimpleName() : (m.length() > 120 ? m.substring(0, 120) : m);
	}
}
