package com.factionsclient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Schematic sharing through Supabase.
 *   /ss upload <file>  -> uploads schematics/<file>, gives a 6-letter code (copied to clipboard)
 *   /ss get <code>     -> downloads it into your schematics folder (Litematica picks it up)
 *   /ss list           -> recent schematics your group shared
 */
public final class SchemShare {
	private static final int MAX_BYTES = 4 * 1024 * 1024;
	private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final SecureRandom RNG = new SecureRandom();

	private SchemShare() { }

	public static Path dir() {
		return FabricLoader.getInstance().getGameDir().resolve("schematics");
	}

	/** Tab-complete file names from the schematics folder. */
	public static final SuggestionProvider<FabricClientCommandSource> FILES = (ctx, builder) -> {
		try (Stream<Path> s = Files.walk(dir(), 3)) {
			s.filter(Files::isRegularFile)
					.map(p -> dir().relativize(p).toString().replace('\\', '/'))
					.filter(n -> n.endsWith(".litematic") || n.endsWith(".schem") || n.endsWith(".schematic") || n.endsWith(".nbt"))
					.forEach(n -> builder.suggest(n.contains(" ") ? "\"" + n + "\"" : n));
		} catch (Exception ignored) {
			// folder missing
		}
		return builder.buildFuture();
	};

	public static int upload(CommandContext<FabricClientCommandSource> ctx) {
		String file = StringArgumentType.getString(ctx, "file");
		if (TeamLink.inTeam()) { // on RelapsedMC in a team: share with the team through the server
			TeamLink.upload(file);
			msg(TeamLink.status, ChatFormatting.LIGHT_PURPLE);
			return 1;
		}
		return uploadFile(file);
	}

	public static int get(CommandContext<FabricClientCommandSource> ctx) {
		String code = StringArgumentType.getString(ctx, "code");
		if (TeamLink.inTeam()) {
			for (TeamLink.Schem s : TeamLink.schems) {
				if (String.valueOf(s.id()).equals(code) || s.name().equalsIgnoreCase(code)) {
					TeamLink.download(s);
					msg(TeamLink.status, ChatFormatting.LIGHT_PURPLE);
					return 1;
				}
			}
		}
		return download(code);
	}

	public static int list(CommandContext<FabricClientCommandSource> ctx) {
		if (TeamLink.inTeam()) {
			if (TeamLink.schems.isEmpty()) msg("Nothing shared with " + TeamLink.team + " yet. /ss upload <file>", ChatFormatting.GRAY);
			for (TeamLink.Schem s : TeamLink.schems) msg("#" + s.id() + "  " + s.name() + "  (" + s.by() + ")  /ss get " + s.id(), ChatFormatting.LIGHT_PURPLE);
			return 1;
		}
		return listToChat();
	}

	/** Schematic files in the schematics folder, relative paths, newest first. */
	public static java.util.List<String> localFiles() {
		try (Stream<Path> s = Files.walk(dir(), 3)) {
			return s.filter(Files::isRegularFile)
					.filter(p -> { String n = p.toString(); return n.endsWith(".litematic") || n.endsWith(".schem") || n.endsWith(".schematic") || n.endsWith(".nbt"); })
					.sorted(java.util.Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed())
					.map(p -> dir().relativize(p).toString().replace('\\', '/'))
					.toList();
		} catch (Exception e) {
			return java.util.List.of();
		}
	}

	public static int uploadFile(String name) {
		if (!ready()) return 0;
		Path file = dir().resolve(name).normalize();
		if (!file.startsWith(dir()) || !Files.isRegularFile(file)) {
			msg("No file named " + name + " in your schematics folder.", ChatFormatting.RED);
			return 0;
		}
		try {
			byte[] data = Files.readAllBytes(file);
			if (data.length > MAX_BYTES) {
				msg("That file is over 4 MB. Too big to share.", ChatFormatting.RED);
				return 0;
			}
			String code = newCode();
			JsonObject row = new JsonObject();
			row.addProperty("code", code);
			row.addProperty("team", FactionsClient.CONFIG.teamKey);
			row.addProperty("name", file.getFileName().toString());
			row.addProperty("uploader", Minecraft.getInstance().getUser().getName());
			row.addProperty("data", Base64.getEncoder().encodeToString(data));

			msg("Uploading " + file.getFileName() + "...", ChatFormatting.GRAY);
			Supa.post("schems", row.toString(), false).whenComplete((v, err) -> Minecraft.getInstance().execute(() -> {
				if (err != null) {
					msg("Upload failed: " + err.getMessage(), ChatFormatting.RED);
				} else {
					Minecraft.getInstance().keyboardHandler.setClipboard(code);
					msg("Shared! Code: " + code + " (copied). Teammates: /ss get " + code, ChatFormatting.GREEN);
				}
			}));
		} catch (Exception e) {
			msg("Couldn't read file: " + e.getMessage(), ChatFormatting.RED);
		}
		return 1;
	}

	public static int download(String rawCode) {
		if (!ready()) return 0;
		String code = rawCode.trim().toUpperCase();
		msg("Downloading " + code + "...", ChatFormatting.GRAY);
		Supa.get("schems?select=name,data&code=eq." + Supa.enc(code) + "&team=eq." + Supa.enc(FactionsClient.CONFIG.teamKey))
				.whenComplete((body, err) -> Minecraft.getInstance().execute(() -> {
					if (err != null) {
						msg("Download failed: " + err.getMessage(), ChatFormatting.RED);
						return;
					}
					JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
					if (arr.isEmpty()) {
						msg("No schematic with code " + code + ".", ChatFormatting.RED);
						return;
					}
					try {
						JsonObject o = arr.get(0).getAsJsonObject();
						String name = Path.of(o.get("name").getAsString()).getFileName().toString();
						Path out = dir().resolve("shared").resolve(name);
						Files.createDirectories(out.getParent());
						if (Files.exists(out)) {
							int dot = name.lastIndexOf('.');
							String base = dot > 0 ? name.substring(0, dot) : name;
							String ext = dot > 0 ? name.substring(dot) : "";
							out = out.resolveSibling(base + "_" + code + ext);
						}
						Files.write(out, Base64.getDecoder().decode(o.get("data").getAsString()));
						msg("Saved to schematics/shared/" + out.getFileName() + ". Load it in Litematica (M).", ChatFormatting.GREEN);
					} catch (Exception e) {
						msg("Couldn't save: " + e.getMessage(), ChatFormatting.RED);
					}
				}));
		return 1;
	}

	public static int listToChat() {
		if (!ready()) return 0;
		Supa.get("schems?select=code,name,uploader,created_at&team=eq." + Supa.enc(FactionsClient.CONFIG.teamKey)
				+ "&order=created_at.desc&limit=10")
				.whenComplete((body, err) -> Minecraft.getInstance().execute(() -> {
					if (err != null) {
						msg("List failed: " + err.getMessage(), ChatFormatting.RED);
						return;
					}
					JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
					if (arr.isEmpty()) {
						msg("Nothing shared yet. Use /ss upload <file>", ChatFormatting.GRAY);
						return;
					}
					msg("Recent shared schematics:", ChatFormatting.AQUA);
					for (JsonElement el : arr) {
						JsonObject o = el.getAsJsonObject();
						msg(o.get("code").getAsString() + "  " + o.get("name").getAsString() + "  by " + o.get("uploader").getAsString(), ChatFormatting.WHITE);
					}
				}));
		return 1;
	}

	private static boolean ready() {
		if (FactionsClient.CONFIG.backendReady()) return true;
		msg("Backend not set up. Edit config/factionsclient.json, then /fc reload", ChatFormatting.RED);
		return false;
	}

	private static String newCode() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 6; i++) sb.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));
		return sb.toString();
	}

	static void msg(String s, ChatFormatting color) {
		Minecraft.getInstance().gui.getChat().addMessage(
				Component.literal("[FC] ").withStyle(ChatFormatting.DARK_AQUA).append(Component.literal(s).withStyle(color)));
	}
}
