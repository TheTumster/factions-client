package com.factionsclient;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/**
 * Discord Rich Presence over Discord's local IPC pipe (no extra library).
 * Shows "Playing laG Client" with the logo, where you are, and time played.
 */
public final class DiscordPresence {
	/** Discord application ID. The app's name ("laG Client") is what shows after "Playing". */
	public static final String DEFAULT_APP_ID = "";

	private static final long START = System.currentTimeMillis() / 1000;
	private static Pipe pipe;
	private static String lastSent = "";
	private static long nextTry = 0;
	private static int ticks = 0;

	private DiscordPresence() { }

	private static String appId() {
		String id = FactionsClient.CONFIG.discordAppId;
		return id == null || id.isBlank() ? DEFAULT_APP_ID : id.trim();
	}

	/** Call every client tick; does real work about once a second. */
	public static void tick(Minecraft mc) {
		if (++ticks < 20) return;
		ticks = 0;
		Config cfg = FactionsClient.CONFIG;
		if (!cfg.discordPresence || appId().isEmpty()) {
			close();
			return;
		}
		String details, state;
		ServerData sd = mc.getCurrentServer();
		if (mc.level == null) {
			details = "In the menus";
			state = null;
		} else if (sd != null) {
			details = cfg.discordShowServer ? "Playing on " + sd.ip.toLowerCase(Locale.ROOT) : "Playing multiplayer";
			state = "Factions";
		} else {
			details = "Singleplayer";
			state = null;
		}
		String key = details + "|" + state;
		if (key.equals(lastSent) && pipe != null) return;

		Thread t = new Thread(() -> send(details, state, key), "laG-Discord");
		t.setDaemon(true);
		t.start();
	}

	private static synchronized void send(String details, String state, String key) {
		try {
			if (pipe == null) {
				if (System.currentTimeMillis() < nextTry) return;
				nextTry = System.currentTimeMillis() + 15_000; // Discord not running: retry later
				pipe = Pipe.open();
				if (pipe == null) return;
				JsonObject hs = new JsonObject();
				hs.addProperty("v", 1);
				hs.addProperty("client_id", appId());
				pipe.write(0, hs.toString());
				pipe.readFrame(); // READY
			}
			JsonObject activity = new JsonObject();
			activity.addProperty("details", details);
			if (state != null) activity.addProperty("state", state);
			JsonObject ts = new JsonObject();
			ts.addProperty("start", START);
			activity.add("timestamps", ts);
			JsonObject assets = new JsonObject();
			assets.addProperty("large_image", "logo");
			assets.addProperty("large_text", "laG Client");
			activity.add("assets", assets);

			JsonObject args = new JsonObject();
			args.addProperty("pid", ProcessHandle.current().pid());
			args.add("activity", activity);
			JsonObject msg = new JsonObject();
			msg.addProperty("cmd", "SET_ACTIVITY");
			msg.add("args", args);
			msg.addProperty("nonce", UUID.randomUUID().toString());
			pipe.write(1, msg.toString());
			pipe.readFrame();
			lastSent = key;
		} catch (Exception e) {
			FactionsClient.LOG.debug("Discord presence: {}", e.toString());
			close();
		}
	}

	public static synchronized void close() {
		if (pipe != null) {
			try {
				pipe.close();
			} catch (IOException ignored) { }
			pipe = null;
			lastSent = "";
		}
	}

	/** Discord's IPC: frames of [int32 opcode][int32 length][utf8 json], little-endian. */
	private abstract static class Pipe implements Closeable {
		abstract void writeRaw(ByteBuffer b) throws IOException;

		abstract void readRaw(ByteBuffer b) throws IOException;

		void write(int op, String json) throws IOException {
			byte[] data = json.getBytes(StandardCharsets.UTF_8);
			ByteBuffer b = ByteBuffer.allocate(8 + data.length).order(ByteOrder.LITTLE_ENDIAN);
			b.putInt(op).putInt(data.length).put(data).flip();
			writeRaw(b);
		}

		void readFrame() throws IOException {
			ByteBuffer head = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
			readRaw(head);
			head.flip();
			head.getInt();
			int len = head.getInt();
			if (len > 0 && len < 1 << 20) readRaw(ByteBuffer.allocate(len));
		}

		static Pipe open() {
			boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
			for (int i = 0; i < 10; i++) {
				try {
					if (windows) return new WinPipe("\\\\?\\pipe\\discord-ipc-" + i);
					for (String dir : unixDirs()) {
						Path p = Path.of(dir, "discord-ipc-" + i);
						if (Files.exists(p)) return new UnixPipe(p);
					}
				} catch (Exception ignored) { }
			}
			return null;
		}

		private static String[] unixDirs() {
			String base = System.getenv("XDG_RUNTIME_DIR");
			if (base == null) base = System.getenv("TMPDIR");
			if (base == null) base = "/tmp";
			return new String[] {base, base + "/app/com.discordapp.Discord", base + "/snap.discord", "/tmp"};
		}
	}

	private static final class WinPipe extends Pipe {
		private final RandomAccessFile f;

		WinPipe(String path) throws IOException {
			f = new RandomAccessFile(path, "rw");
		}

		@Override
		void writeRaw(ByteBuffer b) throws IOException {
			f.write(b.array(), b.position(), b.remaining());
		}

		@Override
		void readRaw(ByteBuffer b) throws IOException {
			f.readFully(b.array(), b.position(), b.remaining());
			b.position(b.limit());
		}

		@Override
		public void close() throws IOException {
			f.close();
		}
	}

	private static final class UnixPipe extends Pipe {
		private final SocketChannel ch;

		UnixPipe(Path p) throws IOException {
			ch = SocketChannel.open(StandardProtocolFamily.UNIX);
			ch.connect(UnixDomainSocketAddress.of(p));
		}

		@Override
		void writeRaw(ByteBuffer b) throws IOException {
			while (b.hasRemaining()) ch.write(b);
		}

		@Override
		void readRaw(ByteBuffer b) throws IOException {
			while (b.hasRemaining()) {
				if (ch.read(b) < 0) throw new IOException("Discord closed the pipe");
			}
		}

		@Override
		public void close() throws IOException {
			ch.close();
		}
	}
}
