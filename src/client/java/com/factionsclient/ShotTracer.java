package com.factionsclient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
 * Shot Tracer: when TNT or sand flies at you (a cannon shot), run its flight backwards through TNT physics
 * (gravity 0.04, drag 0.98 per tick) until it hits the block it came out of. That gives the line the shot
 * travelled and where the cannon is, even if the cannon itself is out of render distance. Shots from the
 * same spot are grouped into one "cannon" marker with a shot count.
 */
public final class ShotTracer {
	/** A traced shot: the backwards path (newest point first) from where we first saw it fly. */
	public static final class Shot {
		public final boolean tnt;
		public final List<Vec3> back = new ArrayList<>();
		public final long at;

		Shot(boolean tnt, long at) {
			this.tnt = tnt;
			this.at = at;
		}
	}

	public static final class Cannon {
		public Vec3 pos;
		public int shots;
		public long last;
		public boolean tnt;

		Cannon(Vec3 pos, long now, boolean tnt) {
			this.pos = pos;
			this.last = now;
			this.tnt = tnt;
			this.shots = 1;
		}
	}

	private static final class Watch {
		Vec3 last, lastStep;
		int fastTicks, logged;
		boolean done;
	}

	public static final List<Shot> SHOTS = new CopyOnWriteArrayList<>();
	public static final List<Cannon> CANNONS = new CopyOnWriteArrayList<>();
	private static final Map<Integer, Watch> WATCH = new HashMap<>();
	private static ClientLevel lastLevel;

	private static final double GRAVITY = 0.04, DRAG = 0.98;
	private static final int MAX_STEPS = 240;
	private static final double MAX_DIST = 320, MERGE = 7;

	private ShotTracer() { }

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level != lastLevel) {
			clear();
			lastLevel = level;
		}
		Config cfg = FactionsClient.CONFIG;
		if (level == null || mc.player == null || !cfg.shotTracer) return;

		long now = System.currentTimeMillis();
		double rangeSq = (double) cfg.crumbRange * cfg.crumbRange;
		double minSpeedSq = cfg.shotMinSpeed / 10.0 * (cfg.shotMinSpeed / 10.0);
		Set<Integer> seen = new HashSet<>();

		for (Entity e : level.entitiesForRendering()) {
			boolean tnt = e instanceof PrimedTnt;
			if (!tnt && !(e instanceof FallingBlockEntity)) continue;
			if (e.distanceToSqr(mc.player) > rangeSq) continue;
			seen.add(e.getId());
			Watch w = WATCH.computeIfAbsent(e.getId(), id -> new Watch());
			if (w.done) continue;
			Vec3 p = e.position();
			if (w.last != null) {
				// Per-tick step from position (the client doesn't always know the real velocity). A shot is two
				// fast steps in a row that roughly agree; a single big jump is just a server position correction.
				Vec3 step = p.subtract(w.last);
				Vec3 vel = e.getDeltaMovement();
				// Real cannon shots can move 10-30+ blocks a tick, so there's no upper speed limit. A single server
				// position correction is filtered out because it won't agree with the step before it, or with the
				// velocity the server told us.
				boolean fast = step.horizontalDistanceSqr() >= minSpeedSq;
				boolean agrees = (w.lastStep != null && w.lastStep.horizontalDistanceSqr() > 0
						&& step.subtract(w.lastStep).lengthSqr() < 0.25 * Math.max(step.lengthSqr(), w.lastStep.lengthSqr()) + 0.02)
						|| (vel.horizontalDistanceSqr() >= minSpeedSq
						&& step.subtract(vel).lengthSqr() < 0.16 * Math.max(step.lengthSqr(), vel.lengthSqr()) + 0.02);
				if (System.getenv("LAGCLIENT_SMOKE") != null && w.logged < 6 && step.lengthSqr() > 0.0001) {
					w.logged++;
					FactionsClient.LOG.info("SMOKE shot-watch id={} step={} v={}", e.getId(), step, e.getDeltaMovement());
				}
				if (fast && agrees) {
					w.done = true;
					trace(level, p, step, tnt, now);
				}
				w.lastStep = step;
			}
			w.last = p;
		}
		WATCH.keySet().retainAll(seen);

		long keep = cfg.shotSeconds * 1000L;
		SHOTS.removeIf(s -> now - s.at > keep);
		CANNONS.removeIf(c -> now - c.last > Math.max(keep, 120_000L));
		if (SHOTS.size() > 600) SHOTS.subList(0, SHOTS.size() - 600).clear();
	}

	/** Run the flight backwards from p, where `move` is the step that brought it to p. */
	private static void trace(ClientLevel level, Vec3 p, Vec3 move, boolean tnt, long now) {
		Shot shot = new Shot(tnt, now);
		Vec3 pos = p;
		Vec3 start = p;
		BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
		for (int i = 0; i < MAX_STEPS; i++) {
			shot.back.add(pos);
			Vec3 prev = pos.subtract(move);
			// move_n = move_{n-1} * drag - gravity  =>  move_{n-1} = (move_n + gravity) / drag
			move = new Vec3(move.x / DRAG, (move.y + GRAVITY) / DRAG, move.z / DRAG);
			bp.set(prev.x, prev.y + 0.49, prev.z);
			if (!level.hasChunkAt(bp)) break;
			if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) break;   // came out of here
			pos = prev;
			if (pos.distanceToSqr(start) > MAX_DIST * MAX_DIST) break;
		}
		// (a trace that stops at once is fine: we first saw it right at the barrel, so that IS the cannon)
		SHOTS.add(shot);
		Vec3 origin = shot.back.get(shot.back.size() - 1);
		for (Cannon c : CANNONS) {
			if (c.pos.distanceToSqr(origin) < MERGE * MERGE) {
				c.pos = c.pos.scale(c.shots / (c.shots + 1.0)).add(origin.scale(1.0 / (c.shots + 1.0)));
				c.shots++;
				c.last = now;
				c.tnt |= tnt;
				return;
			}
		}
		CANNONS.add(new Cannon(origin, now, tnt));
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && FactionsClient.CONFIG.shotAlert) {
			double dx = origin.x - mc.player.getX(), dz = origin.z - mc.player.getZ();
			String[] dirs = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
			String dir = dirs[(int) Math.floorMod(Math.round(Math.toDegrees(Math.atan2(-dx, dz)) / 45.0), 8)];
			mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT,
					"§c§lCANNON §7shooting from §f%d %d %d §7(%dm %s)", (int) Math.floor(origin.x), (int) Math.floor(origin.y),
					(int) Math.floor(origin.z), (int) Math.sqrt(dx * dx + dz * dz), dir)), false);
		}
	}

	/** The cannon that fired most recently, for the HUD. */
	public static Cannon latest() {
		Cannon best = null;
		for (Cannon c : CANNONS) if (best == null || c.last > best.last) best = c;
		return best;
	}

	public static void clear() {
		SHOTS.clear();
		CANNONS.clear();
		WATCH.clear();
	}
}
