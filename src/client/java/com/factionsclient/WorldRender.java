package com.factionsclient;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

/** Draws crumb trails, explosion markers and teammate beacons in the world. */
public final class WorldRender {
	private static final int MY_BOOM = 0x90FF1010;     // red
	private static final int TEAM_BOOM = 0x90B040FF;   // purple (teammate saw it)
	private static final int MATE = 0x8038E0FF;        // cyan

	private WorldRender() { }

	public static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		Config cfg = FactionsClient.CONFIG;

		Vec3 cam = context.worldState().cameraRenderState.pos;
		PoseStack matrices = context.matrices();
		VertexConsumer vc = context.consumers().getBuffer(RenderTypes.debugFilledBox());
		Matrix4f m = matrices.last().pose();

		if (cfg.crumbs) {
			for (Crumbs.Trail t : Crumbs.ACTIVE.values()) trail(m, vc, cam, t);
			for (Crumbs.Trail t : Crumbs.FINISHED) trail(m, vc, cam, t);
			for (Crumbs.Marker mk : Crumbs.MARKERS) {
				// Explosion spot: highlight the whole block
				double bx = Math.floor(mk.pos().x), by = Math.floor(mk.pos().y), bz = Math.floor(mk.pos().z);
				box(m, vc, cam, bx - 0.01, by - 0.01, bz - 0.01, bx + 1.01, by + 1.01, bz + 1.01, mk.shared() ? TEAM_BOOM : MY_BOOM);
			}
		}

		if (cfg.chunkGrid) chunkBorders(m, vc, cam, mc);
		if (cfg.claimMap) claimBorders(m, vc, cam, mc);
		if (cfg.homeBeacon) homeBeacon(m, vc, cam, mc);

		if (cfg.team) {
			String dim = ServerLink.dimId(mc);
			for (ServerLink.Mate mate : ServerLink.MATES) {
				if (!mate.dim().equals(dim)) continue;
				// Tall pillar so you can spot them from far away, with a bright marker at head height
				double d = Math.sqrt(mc.player.distanceToSqr(mate.x(), mate.y(), mate.z()));
				double r = 0.2 + Math.min(0.6, d / 200.0);
				box(m, vc, cam, mate.x() - r, mate.y(), mate.z() - r, mate.x() + r, mate.y() + 48, mate.z() + r, MATE);
				box(m, vc, cam, mate.x() - 0.3, mate.y() + 2.1, mate.z() - 0.3, mate.x() + 0.3, mate.y() + 2.7, mate.z() + 0.3, 0xE038E0FF);
			}
		}

		// RelapsedMC team: pink pillars (any realm), skip anyone already drawn as a faction mate
		if (cfg.teamTrack) {
			String dim = ServerLink.dimId(mc);
			for (ServerLink.Mate mate : TeamLink.MATES) {
				if (!mate.dim().equals(dim) || ServerLink.MATES.stream().anyMatch(f -> f.name().equals(mate.name()))) continue;
				double d = Math.sqrt(mc.player.distanceToSqr(mate.x(), mate.y(), mate.z()));
				double r = 0.2 + Math.min(0.6, d / 200.0);
				box(m, vc, cam, mate.x() - r, mate.y(), mate.z() - r, mate.x() + r, mate.y() + 48, mate.z() + r, 0x80FF4DD2);
				box(m, vc, cam, mate.x() - 0.3, mate.y() + 2.1, mate.z() - 0.3, mate.x() + 0.3, mate.y() + 2.7, mate.z() + 0.3, 0xE0FF4DD2);
			}
		}

		// Team pings: a pulsing marker + short beam, red for "enemy"
		if (cfg.teamPings && !TeamLink.PINGS.isEmpty()) {
			String dim = ServerLink.dimId(mc);
			long now = System.currentTimeMillis();
			for (TeamLink.Ping p : TeamLink.PINGS) {
				if (!p.dim().equals(dim)) continue;
				float age = (now - p.at()) / 12_000f;
				int a = (int) (0xE0 * Math.max(0, 1 - age * age));
				int rgb = p.kind().equals("enemy") ? 0xFF3B4E : 0xFFC23D;
				double pulse = 0.35 + 0.15 * Math.sin(now / 150.0);
				box(m, vc, cam, p.x() - pulse, p.y() - pulse + 0.5, p.z() - pulse, p.x() + pulse, p.y() + pulse + 0.5, p.z() + pulse, alpha(rgb, a));
				box(m, vc, cam, p.x() - 0.08, p.y() + 1, p.z() - 0.08, p.x() + 0.08, p.y() + 24, p.z() + 0.08, alpha(rgb, a / 2));
			}
		}
	}

	private static final int CHUNK_CORNER = 0xC0FF3030; // red: neighbor chunk corners

	/** Factions claim view: current chunk walls with a grid, corner posts for the 3x3 around you. */
	private static void chunkBorders(Matrix4f m, VertexConsumer vc, Vec3 cam, Minecraft mc) {
		int CHUNK_EDGE = alpha(FactionsClient.CONFIG.chunkColor, 0xC0);
		int CHUNK_GRID = alpha(FactionsClient.CONFIG.chunkColor, 0x50);
		int cx = mc.player.getBlockX() >> 4;
		int cz = mc.player.getBlockZ() >> 4;
		double x0 = cx << 4, z0 = cz << 4, x1 = x0 + 16, z1 = z0 + 16;
		double py = Math.floor(mc.player.getY());
		int hh = FactionsClient.CONFIG.chunkHeight;
		double yMin = Math.max(mc.level.getMinY(), py - hh);
		double yMax = py + hh;
		double t = 0.025;

		// Vertical lines along your chunk's walls (thicker at the corners)
		for (int i = 0; i <= 16; i++) {
			double w = (i == 0 || i == 16) ? 0.06 : t;
			int col = (i == 0 || i == 16) ? CHUNK_EDGE : CHUNK_GRID;
			box(m, vc, cam, x0 + i - w, yMin, z0 - w, x0 + i + w, yMax, z0 + w, col);
			box(m, vc, cam, x0 + i - w, yMin, z1 - w, x0 + i + w, yMax, z1 + w, col);
			box(m, vc, cam, x0 - w, yMin, z0 + i - w, x0 + w, yMax, z0 + i + w, col);
			box(m, vc, cam, x1 - w, yMin, z0 + i - w, x1 + w, yMax, z0 + i + w, col);
		}
		// Horizontal lines every 2 blocks
		for (double y = Math.ceil(yMin / 2) * 2; y <= yMax; y += 2) {
			int col = y == py ? CHUNK_EDGE : CHUNK_GRID;
			box(m, vc, cam, x0, y - t, z0 - t, x1, y + t, z0 + t, col);
			box(m, vc, cam, x0, y - t, z1 - t, x1, y + t, z1 + t, col);
			box(m, vc, cam, x0 - t, y - t, z0, x0 + t, y + t, z1, col);
			box(m, vc, cam, x1 - t, y - t, z0, x1 + t, y + t, z1, col);
		}
		// Red corner posts for the surrounding chunks
		if (FactionsClient.CONFIG.chunkCorners) for (int dx = -1; dx <= 2; dx++) {
			for (int dz = -1; dz <= 2; dz++) {
				double x = x0 + dx * 16, z = z0 + dz * 16;
				if ((dx == 0 || dx == 1) && (dz == 0 || dz == 1)) continue; // your own corners
				box(m, vc, cam, x - 0.05, yMin, z - 0.05, x + 0.05, yMax, z + 0.05, CHUNK_CORNER);
			}
		}
	}

	/** Relation colors (RGB) used by the claim map and the territory HUD. */
	public static int relationColor(int rel) {
		return switch (rel) {
			case ServerLink.OWN -> 0x4DFF88;      // green
			case ServerLink.ALLY -> 0xB040FF;     // purple
			case ServerLink.TRUCE -> 0x38E0FF;    // cyan
			case ServerLink.ENEMY -> 0xFF3B4E;    // red
			case ServerLink.SAFEZONE -> 0xFFC23D; // gold
			case ServerLink.WARZONE -> 0xFF7A1A;  // orange
			default -> 0xE6E6F0;                  // neutral
		};
	}

	/**
	 * Faction claims around you: a soft translucent wall on every edge where the owner changes,
	 * with a bright line along the top and bottom. Wilderness edges of a claim get a wall too.
	 */
	private static void claimBorders(Matrix4f m, VertexConsumer vc, Vec3 cam, Minecraft mc) {
		var claims = ServerLink.CLAIMS;
		if (claims.isEmpty() || !ServerLink.CLAIMS_DIM.equals(ServerLink.dimId(mc))) return;
		double py = Math.floor(mc.player.getY());
		int hh = Math.max(2, FactionsClient.CONFIG.claimWallHeight);
		double y0 = py - 1, y1 = py + hh;
		double t = 0.02;
		for (var e : claims.entrySet()) {
			int cx = ServerLink.chunkX(e.getKey()), cz = ServerLink.chunkZ(e.getKey());
			ServerLink.Claim c = e.getValue();
			int rgb = relationColor(c.rel());
			int wall = alpha(rgb, 0x3A), edge = alpha(rgb, 0xE8);
			double x0 = cx << 4, z0 = cz << 4, x1 = x0 + 16, z1 = z0 + 16;
			// north (-z), south (+z), west (-x), east (+x)
			if (differs(c, claims.get(ServerLink.chunkKey(cx, cz - 1)))) wallX(m, vc, cam, x0, x1, z0, y0, y1, t, wall, edge);
			if (differs(c, claims.get(ServerLink.chunkKey(cx, cz + 1)))) wallX(m, vc, cam, x0, x1, z1, y0, y1, t, wall, edge);
			if (differs(c, claims.get(ServerLink.chunkKey(cx - 1, cz)))) wallZ(m, vc, cam, z0, z1, x0, y0, y1, t, wall, edge);
			if (differs(c, claims.get(ServerLink.chunkKey(cx + 1, cz)))) wallZ(m, vc, cam, z0, z1, x1, y0, y1, t, wall, edge);
		}
	}

	private static boolean differs(ServerLink.Claim a, ServerLink.Claim b) {
		return b == null || b.rel() != a.rel() || !b.tag().equals(a.tag());
	}

	private static void wallX(Matrix4f m, VertexConsumer vc, Vec3 cam, double xa, double xb, double z, double y0, double y1, double t, int wall, int edge) {
		box(m, vc, cam, xa, y0, z - t, xb, y1, z + t, wall);
		box(m, vc, cam, xa, y0 - 0.05, z - 0.06, xb, y0 + 0.05, z + 0.06, edge);
		box(m, vc, cam, xa, y1 - 0.05, z - 0.06, xb, y1 + 0.05, z + 0.06, edge);
		// posts every 4 blocks so the wall reads from far away
		for (double x = xa; x <= xb + 0.01; x += 4) box(m, vc, cam, x - 0.05, y0, z - 0.05, x + 0.05, y1, z + 0.05, edge);
	}

	private static void wallZ(Matrix4f m, VertexConsumer vc, Vec3 cam, double za, double zb, double x, double y0, double y1, double t, int wall, int edge) {
		box(m, vc, cam, x - t, y0, za, x + t, y1, zb, wall);
		box(m, vc, cam, x - 0.06, y0 - 0.05, za, x + 0.06, y0 + 0.05, zb, edge);
		box(m, vc, cam, x - 0.06, y1 - 0.05, za, x + 0.06, y1 + 0.05, zb, edge);
		for (double z = za; z <= zb + 0.01; z += 4) box(m, vc, cam, x - 0.05, y0, z - 0.05, x + 0.05, y1, z + 0.05, edge);
	}

	/** A tall gradient beam at your faction's /f home, visible from far away. */
	private static void homeBeacon(Matrix4f m, VertexConsumer vc, Vec3 cam, Minecraft mc) {
		ServerLink.Home h = ServerLink.HOME;
		if (h == null || !h.dim().equals(ServerLink.dimId(mc))) return;
		double bx = Math.floor(h.x()) + 0.5, bz = Math.floor(h.z()) + 0.5, by = Math.floor(h.y());
		double dist = Math.sqrt(mc.player.distanceToSqr(bx, by, bz));
		if (dist < 6) return; // you're there
		double r = 0.35 + Math.min(1.0, dist / 160.0); // thicker far away so it stays visible
		double core = r * 0.35;
		int segs = 12;
		for (int i = 0; i < segs; i++) {
			int col = com.factionsclient.ui.Theme.gradientAt(i / (float) (segs - 1));
			double ya = by + i * 16, yb = ya + 16;
			box(m, vc, cam, bx - r, ya, bz - r, bx + r, yb, bz + r, alpha(col, 0x58));
			box(m, vc, cam, bx - core, ya, bz - core, bx + core, yb, bz + core, alpha(col, 0xE0));
		}
		box(m, vc, cam, bx - 0.5, by, bz - 0.5, bx + 0.5, by + 0.05, bz + 0.5, alpha(0x4DFF88, 0xA0));
	}

	private static int alpha(int rgb, int a) {
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	private static void trail(Matrix4f m, VertexConsumer vc, Vec3 cam, Crumbs.Trail t) {
		int color = alpha(t.tnt ? FactionsClient.CONFIG.tntColor : FactionsClient.CONFIG.sandColor, 0xB0);
		double s = t.tnt ? 0.09 : 0.07;
		for (Vec3 p : t.points) {
			// Entity position is the bottom-center; lift the dot to the middle of the block-sized entity
			double y = p.y + 0.49;
			box(m, vc, cam, p.x - s, y - s, p.z - s, p.x + s, y + s, p.z + s, color);
		}
	}

	/** Filled box in world coordinates (converted to camera-relative to keep float precision). */
	private static void box(Matrix4f m, VertexConsumer vc, Vec3 cam,
			double x1d, double y1d, double z1d, double x2d, double y2d, double z2d, int c) {
		float x1 = (float) (x1d - cam.x), y1 = (float) (y1d - cam.y), z1 = (float) (z1d - cam.z);
		float x2 = (float) (x2d - cam.x), y2 = (float) (y2d - cam.y), z2 = (float) (z2d - cam.z);

		// Front (min Z)
		vc.addVertex(m, x1, y1, z1).setColor(c);
		vc.addVertex(m, x2, y1, z1).setColor(c);
		vc.addVertex(m, x2, y2, z1).setColor(c);
		vc.addVertex(m, x1, y2, z1).setColor(c);
		// Back (max Z)
		vc.addVertex(m, x2, y1, z2).setColor(c);
		vc.addVertex(m, x1, y1, z2).setColor(c);
		vc.addVertex(m, x1, y2, z2).setColor(c);
		vc.addVertex(m, x2, y2, z2).setColor(c);
		// Left (min X)
		vc.addVertex(m, x1, y1, z2).setColor(c);
		vc.addVertex(m, x1, y1, z1).setColor(c);
		vc.addVertex(m, x1, y2, z1).setColor(c);
		vc.addVertex(m, x1, y2, z2).setColor(c);
		// Right (max X)
		vc.addVertex(m, x2, y1, z1).setColor(c);
		vc.addVertex(m, x2, y1, z2).setColor(c);
		vc.addVertex(m, x2, y2, z2).setColor(c);
		vc.addVertex(m, x2, y2, z1).setColor(c);
		// Top
		vc.addVertex(m, x1, y2, z1).setColor(c);
		vc.addVertex(m, x2, y2, z1).setColor(c);
		vc.addVertex(m, x2, y2, z2).setColor(c);
		vc.addVertex(m, x1, y2, z2).setColor(c);
		// Bottom
		vc.addVertex(m, x1, y1, z2).setColor(c);
		vc.addVertex(m, x2, y1, z2).setColor(c);
		vc.addVertex(m, x2, y1, z1).setColor(c);
		vc.addVertex(m, x1, y1, z1).setColor(c);
	}
}
