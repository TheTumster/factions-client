package com.factionsclient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Keeps the whole pack up to date. On launch it reads manifest.json from the latest GitHub release,
 * downloads any new/changed jars next to the old ones as *.jar.fcupdate (the game ignores those),
 * and swaps them in after the game closes. Next launch = new version. Only touches jars the pack manages.
 */
public final class Updater {
	public static final String MANIFEST_URL =
			"https://github.com/TheTumster/factions-client/releases/latest/download/manifest.json";
	private static final String SUFFIX = ".fcupdate";

	private static final HttpClient HTTP = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	/** null = nothing pending; otherwise a short description for the HUD/menu. */
	public static volatile String pending = null;
	private static volatile boolean announced = false;

	private Updater() { }

	private static Path modsDir() {
		return FabricLoader.getInstance().getGameDir().resolve("mods");
	}

	private static Path stateFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("factionsclient-managed.json");
	}

	/** Runs in the background at startup. */
	public static void checkAsync() {
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) return;
		Thread t = new Thread(() -> {
			try {
				check();
			} catch (Exception e) {
				FactionsClient.LOG.warn("Update check failed: {}", e.toString());
			}
		}, "FactionsClient-Updater");
		t.setDaemon(true);
		t.start();
	}

	private static void check() throws Exception {
		JsonObject manifest = JsonParser.parseString(fetchString(MANIFEST_URL)).getAsJsonObject();
		String build = manifest.get("build").getAsString();
		Path mods = modsDir();
		Files.createDirectories(mods);

		Set<String> wanted = new HashSet<>();
		List<String> downloaded = new ArrayList<>();
		for (JsonElement el : manifest.getAsJsonArray("mods")) {
			JsonObject m = el.getAsJsonObject();
			String name = m.get("filename").getAsString();
			if (name.contains("/") || name.contains("\\") || !name.endsWith(".jar")) continue; // safety
			wanted.add(name);
			Path target = mods.resolve(name);
			String sha1 = m.get("sha1").getAsString().toLowerCase(Locale.ROOT);
			if (Files.exists(target) && sha1.equals(sha1(target))) continue;

			Path staged = mods.resolve(name + SUFFIX);
			if (Files.exists(staged) && sha1.equals(sha1(staged))) {
				downloaded.add(name);
				continue;
			}
			download(m.get("url").getAsString(), staged);
			if (!sha1.equals(sha1(staged))) {
				Files.deleteIfExists(staged);
				throw new IOException("Checksum mismatch for " + name);
			}
			downloaded.add(name);
			FactionsClient.LOG.info("Downloaded update: {}", name);
		}

		// Jars from an older version of the pack that aren't in the new one get removed.
		Set<String> previous = readState();
		List<String> obsolete = new ArrayList<>();
		for (String old : previous) {
			if (!wanted.contains(old) && Files.exists(mods.resolve(old))) obsolete.add(old);
		}
		// Always clean up older copies of this client itself, whatever the saved state says.
		try (var stream = Files.list(mods)) {
			stream.map(f -> f.getFileName().toString())
					.filter(n -> n.startsWith("factionsclient-") && n.endsWith(".jar") && !wanted.contains(n) && !obsolete.contains(n))
					.forEach(obsolete::add);
		}
		writeState(wanted);

		if (downloaded.isEmpty() && obsolete.isEmpty()) {
			FactionsClient.LOG.info("RelapsedMC Client is up to date (build {})", build);
			return;
		}
		scheduleSwap(mods, downloaded, obsolete);
		pending = "Update downloaded (build " + build + "). Restart the game to apply.";
		announce();
	}

	/** Swapping a jar the game is using only works after it exits (Windows locks open files). */
	private static void scheduleSwap(Path mods, List<String> downloaded, List<String> obsolete) throws IOException {
		boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
		if (!windows) {
			// macOS/Linux let us replace open files; do it when the game shuts down.
			Runtime.getRuntime().addShutdownHook(new Thread(() -> applyNow(mods, downloaded, obsolete)));
			return;
		}
		long pid = ProcessHandle.current().pid();
		StringBuilder bat = new StringBuilder();
		bat.append("@echo off\r\n");
		bat.append(":wait\r\n");
		bat.append("tasklist /FI \"PID eq ").append(pid).append("\" 2>nul | find \"").append(pid).append("\" >nul\r\n");
		bat.append("if not errorlevel 1 (timeout /t 1 /nobreak >nul & goto wait)\r\n");
		bat.append("cd /d \"").append(mods.toAbsolutePath()).append("\"\r\n");
		for (String o : obsolete) bat.append("del /f /q \"").append(o).append("\"\r\n");
		for (String d : downloaded) {
			bat.append("if exist \"").append(d).append(SUFFIX).append("\" (del /f /q \"").append(d).append("\" >nul 2>&1 & ren \"")
					.append(d).append(SUFFIX).append("\" \"").append(d).append("\")\r\n");
		}
		bat.append("del \"%~f0\"\r\n");
		Path script = FabricLoader.getInstance().getGameDir().resolve("factionsclient-update.bat");
		Files.writeString(script, bat.toString(), StandardCharsets.US_ASCII);
		new ProcessBuilder("cmd", "/c", "start", "\"\"", "/min", script.toAbsolutePath().toString()).start();
	}

	private static void applyNow(Path mods, List<String> downloaded, List<String> obsolete) {
		for (String o : obsolete) {
			try {
				Files.deleteIfExists(mods.resolve(o));
			} catch (IOException ignored) { }
		}
		for (String d : downloaded) {
			try {
				Files.move(mods.resolve(d + SUFFIX), mods.resolve(d), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) { }
		}
	}

	/** Tells the player in chat once they're in a world. */
	public static void announce() {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (pending != null && !announced && mc.player != null) {
				announced = true;
				SchemShare.msg(pending, ChatFormatting.GREEN);
			}
		});
	}

	// ---- helpers ----

	private static String fetchString(String url) throws Exception {
		HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode() + " for " + url);
		return r.body();
	}

	private static void download(String url, Path to) throws Exception {
		HttpResponse<InputStream> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5)).GET().build(),
				HttpResponse.BodyHandlers.ofInputStream());
		if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode() + " for " + url);
		try (InputStream in = r.body()) {
			Files.copy(in, to, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static String sha1(Path p) throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		try (InputStream in = Files.newInputStream(p)) {
			byte[] buf = new byte[65536];
			int n;
			while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
		}
		return HexFormat.of().formatHex(md.digest());
	}

	private static Set<String> readState() {
		Set<String> s = new HashSet<>();
		try {
			if (Files.exists(stateFile())) {
				for (JsonElement e : JsonParser.parseString(Files.readString(stateFile())).getAsJsonArray()) s.add(e.getAsString());
			}
		} catch (Exception ignored) { }
		return s;
	}

	private static void writeState(Set<String> names) {
		try {
			JsonArray a = new JsonArray();
			names.stream().sorted().forEach(a::add);
			Files.writeString(stateFile(), a.toString());
		} catch (IOException ignored) { }
	}
}
