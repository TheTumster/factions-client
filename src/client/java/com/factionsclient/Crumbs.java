package com.factionsclient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.Vec3;

/**
 * Tracks every primed TNT and falling block (sand/gravel/concrete powder) near you and keeps
 * its path as a trail. When TNT goes off, the spot is saved as a "patch crumb" marker.
 */
public final class Crumbs {
	public static final class Trail {
		public final boolean tnt;
		public final List<Vec3> points = new ArrayList<>();
		public long endedAt = -1;
		int lastFuse = 80;

		Trail(boolean tnt) {
			this.tnt = tnt;
		}
	}

	/** A single point of interest, e.g. where TNT exploded. shared = came from a teammate. */
	public record Marker(Vec3 pos, long time, boolean shared, String by) { }

	private static final int MAX_POINTS = 400;
	private static final double MIN_STEP_SQ = 0.2 * 0.2;

	public static final Map<Integer, Trail> ACTIVE = new HashMap<>();
	public static final List<Trail> FINISHED = new ArrayList<>();
	public static final List<Marker> MARKERS = new CopyOnWriteArrayList<>();
	/** Explosion block positions waiting to be sent to the team. */
	public static final Set<BlockPos> OUTBOX = new HashSet<>();

	private static ClientLevel lastLevel;

	private Crumbs() { }

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level != lastLevel) {
			clear();
			lastLevel = level;
		}
		if (level == null || mc.player == null || !FactionsClient.CONFIG.crumbs) return;

		long now = System.currentTimeMillis();
		double range = FactionsClient.CONFIG.crumbRange;
		double rangeSq = range * range;
		Set<Integer> seen = new HashSet<>();

		for (Entity e : level.entitiesForRendering()) {
			boolean tnt = e instanceof PrimedTnt;
			if (!tnt && !(e instanceof FallingBlockEntity)) continue;
			if (e.distanceToSqr(mc.player) > rangeSq) continue;

			seen.add(e.getId());
			Trail t = ACTIVE.computeIfAbsent(e.getId(), id -> new Trail(tnt));
			Vec3 p = e.position();
			if (t.points.isEmpty() || t.points.get(t.points.size() - 1).distanceToSqr(p) > MIN_STEP_SQ) {
				if (t.points.size() >= MAX_POINTS) t.points.remove(0);
				t.points.add(p);
			}
			if (e instanceof PrimedTnt primed) t.lastFuse = primed.getFuse();
		}

		// Anything we stopped seeing has landed, exploded or left range.
		Iterator<Map.Entry<Integer, Trail>> it = ACTIVE.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Trail> entry = it.next();
			if (seen.contains(entry.getKey())) continue;
			Trail t = entry.getValue();
			t.endedAt = now;
			FINISHED.add(t);
			it.remove();

			// Fuse ran out => it exploded here. (A high fuse means it just unloaded.)
			if (t.tnt && t.lastFuse <= 2 && !t.points.isEmpty()) {
				Vec3 end = t.points.get(t.points.size() - 1);
				MARKERS.add(new Marker(end, now, false, null));
				if (FactionsClient.CONFIG.shareCrumbs) {
					synchronized (OUTBOX) {
						OUTBOX.add(BlockPos.containing(end));
					}
				}
			}
		}

		long maxAge = FactionsClient.CONFIG.crumbSeconds * 1000L;
		FINISHED.removeIf(t -> now - t.endedAt > maxAge);
		MARKERS.removeIf(m -> now - m.time() > maxAge);
		if (FINISHED.size() > 3000) FINISHED.subList(0, FINISHED.size() - 3000).clear();
	}

	public static int liveTnt() {
		int n = 0;
		for (Trail t : ACTIVE.values()) if (t.tnt) n++;
		return n;
	}

	public static void clear() {
		ACTIVE.clear();
		FINISHED.clear();
		MARKERS.clear();
		synchronized (OUTBOX) {
			OUTBOX.clear();
		}
	}
}
