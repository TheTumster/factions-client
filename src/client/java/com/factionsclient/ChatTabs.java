package com.factionsclient;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

import com.factionsclient.ui.Theme;

/**
 * TabbyChat-style chat tabs, built for RelapsedMC.
 *
 * All / Faction / Ally / Private / Server, plus your own tabs (a name and a regex). A message can be in several
 * tabs (All shows everything). Picking a tab filters the chat; typing in Faction or Ally switches your FactionsUUID
 * chat mode for you, and typing in Private replies to whoever messaged you last.
 */
public final class ChatTabs {
	public record Tab(String name, Pattern pattern, int color, boolean builtin) { }

	// FactionsUUID's chat formats ("[faction-only] ...", "[member+] ...", "[ally-only] ...")
	private static final Pattern FACTION = Pattern.compile("^\\s*\\[(faction-only|[a-z]+\\+)\\]", Pattern.CASE_INSENSITIVE);
	private static final Pattern ALLY = Pattern.compile("^\\s*\\[(ally|truce)-only\\]", Pattern.CASE_INSENSITIVE);
	// Vanilla /msg ("X whispers to you: ...", "You whisper to X: ..."), and the common "[X -> me]" style
	private static final Pattern PRIVATE = Pattern.compile(
			"(whispers to you:|^You whisper to |->\\s*me\\]|^\\[me\\s*->)", Pattern.CASE_INSENSITIVE);
	private static final Pattern FROM_PARTNER = Pattern.compile(
			"^(?:\\[\\S*\\]\\s*)*(\\w{2,16}) whispers to you:|^\\[(\\w{2,16})\\s*->\\s*me\\]|^You whisper to (\\w{2,16}):|^\\[me\\s*->\\s*(\\w{2,16})\\]",
			Pattern.CASE_INSENSITIVE);
	// Player chat: RelapsedMC's "Name » message" and vanilla "<Name> message"
	private static final Pattern PLAYER_CHAT = Pattern.compile(" » |^<\\w{2,16}> ");

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	private static List<Tab> tabs = List.of();
	private static int active = 0;
	private static final Map<String, Integer> unread = new LinkedHashMap<>();
	/** Who to reply to from the Private tab. */
	private static String partner = null;
	/** Last /f chat mode we put the server in this session ("public", "faction", "ally"). */
	private static String serverMode = "public";
	private static String lastText = null;
	private static int repeat = 1;

	private ChatTabs() { }

	public static void rebuild() {
		Config c = FactionsClient.CONFIG;
		List<Tab> t = new ArrayList<>();
		t.add(new Tab("All", null, Theme.TEXT, true));
		if (c.chatTabFaction) t.add(new Tab("Faction", FACTION, 0xFF4DFF88, true));
		if (c.chatTabAlly) t.add(new Tab("Ally", ALLY, 0xFFB040FF, true));
		if (c.chatTabPrivate) t.add(new Tab("Private", PRIVATE, 0xFFFF4DD2, true));
		if (c.chatTabServer) t.add(new Tab("Server", null, 0xFFFFC23D, true));
		for (String spec : c.chatCustomTabs) {
			int i = spec.indexOf(':');
			if (i <= 0) continue;
			try {
				t.add(new Tab(spec.substring(0, i).trim(), Pattern.compile(spec.substring(i + 1).trim(), Pattern.CASE_INSENSITIVE),
						0xFF38E0FF, false));
			} catch (Exception ignored) {
				// bad regex: skip that tab
			}
		}
		tabs = t;
		if (active >= tabs.size()) active = 0;
	}

	public static boolean enabled() {
		return FactionsClient.CONFIG.chatTabs;
	}

	static boolean inTab(Tab tab, String text) {
		if (tab.pattern() != null) return tab.pattern().matcher(text).find();
		if (tab.name().equals("Server")) {
			return !PLAYER_CHAT.matcher(text).find() && !FACTION.matcher(text).find() && !ALLY.matcher(text).find()
					&& !PRIVATE.matcher(text).find();
		}
		return true; // All
	}

	/** Should this line show right now? (chat rebuilds call this for every stored message) */
	public static boolean visible(Component msg) {
		if (!enabled() || tabs.isEmpty() || active == 0) return true;
		return inTab(tabs.get(active), msg.getString());
	}

	/**
	 * A new message arrived: count unread, remember who whispered, add a timestamp, stack spam.
	 * Returns the component to actually add, or null to drop it (stacked into the previous line).
	 */
	public static Component incoming(Component msg) {
		if (!enabled()) return msg;
		if (tabs.isEmpty()) rebuild();
		String text = msg.getString();
		Matcher pm = FROM_PARTNER.matcher(text);
		if (pm.find()) {
			for (int g = 1; g <= pm.groupCount(); g++) if (pm.group(g) != null) partner = pm.group(g);
			if (FactionsClient.CONFIG.chatPrivatePing && (pm.group(1) != null || pm.group(2) != null)) {
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.6f, 0.6f));
			}
		}
		for (int i = 1; i < tabs.size(); i++) {
			if (i != active && inTab(tabs.get(i), text)) unread.merge(tabs.get(i).name(), 1, Integer::sum);
		}
		MutableComponent out = msg.copy();
		if (FactionsClient.CONFIG.chatTimestamps) {
			out = Component.literal("[" + LocalTime.now().format(TIME) + "] ").withStyle(ChatFormatting.DARK_GRAY).append(msg);
		}
		return out;
	}

	/** Spam stacking: the same line again replaces the previous one with "[x3]". */
	public static boolean stackRepeat(String text) {
		if (!enabled() || !FactionsClient.CONFIG.chatStack) return false;
		boolean same = text.equals(lastText);
		repeat = same ? repeat + 1 : 1;
		lastText = text;
		return same;
	}

	public static Component withCount(Component msg) {
		return msg.copy().append(Component.literal(" [x" + repeat + "]").withStyle(ChatFormatting.GRAY));
	}

	public static void select(int i) {
		if (i < 0 || i >= tabs.size() || i == active) return;
		active = i;
		unread.remove(tabs.get(i).name());
		Minecraft.getInstance().gui.getChat().rescaleChat(); // rebuild visible lines through the filter
	}

	public static void reset() {
		active = 0;
		unread.clear();
		partner = null;
		serverMode = "public";
		lastText = null;
		repeat = 1;
		rebuild();
	}

	// ---------------------------------------------------------------- sending

	/**
	 * You pressed Enter in chat. Returns true if we sent it ourselves (Private reply), false to let vanilla send.
	 * Faction/Ally tabs first switch your /f chat mode so the message goes to the right people.
	 */
	public static boolean onSend(String msg) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return false;
		if (msg.startsWith("/")) {
			String cmd = msg.substring(1).toLowerCase(java.util.Locale.ROOT);
			// You changed the mode yourself: remember it so we don't fight you
			Matcher m = Pattern.compile("^f(?:actions?)?\\s+(?:c|chat)\\s+(\\w+)").matcher(cmd);
			if (m.find()) serverMode = normalizeMode(m.group(1));
			return false;
		}
		if (!enabled() || !FactionsClient.CONFIG.chatRoute || tabs.isEmpty()) return false;
		String tab = tabs.get(active).name();
		boolean factions = "factions".equalsIgnoreCase(ServerLink.REALM);
		if (factions) {
			String want = tab.equals("Faction") ? "faction" : tab.equals("Ally") ? "ally" : "public";
			if (!want.equals(serverMode)) {
				mc.player.connection.sendCommand("f chat " + want);
				serverMode = want;
			}
		}
		if (tab.equals("Private") && partner != null) {
			mc.player.connection.sendCommand("msg " + partner + " " + msg);
			return true;
		}
		return false;
	}

	private static String normalizeMode(String s) {
		return switch (s) {
			case "f", "fac", "faction", "member", "mod", "coleader" -> "faction";
			case "a", "ally", "t", "truce" -> "ally";
			default -> "public";
		};
	}

	// ---------------------------------------------------------------- drawing

	private static final List<int[]> HITS = new ArrayList<>();

	/** Tab bar over the chat, while chat is open. */
	public static void renderBar(GuiGraphics g, Font font, int screenH, int mouseX, int mouseY) {
		HITS.clear();
		if (!enabled()) return;
		if (tabs.isEmpty()) rebuild();
		int x = 2, y = screenH - 27, h = 11;
		for (int i = 0; i < tabs.size(); i++) {
			Tab t = tabs.get(i);
			int n = unread.getOrDefault(t.name(), 0);
			String label = t.name() + (n > 0 ? " " + Math.min(n, 99) : "");
			int w = font.width(label) + 10;
			boolean on = i == active, hov = Theme.inside(mouseX, mouseY, x, y, w, h);
			Theme.rect(g, x, y, w, h, on ? 0xE0161026 : hov ? 0xC0161026 : 0x900B0716);
			if (on) {
				Theme.gradient(g, x, y + h - 1, w, 1, 1f);
			} else if (n > 0) {
				g.fill(x, y + h - 1, x + w, y + h, Theme.alpha(t.color(), 0.9f));
			}
			int tc = on ? 0xFFFFFFFF : n > 0 ? t.color() : Theme.MUTED;
			g.drawString(font, label, x + 5, y + 2, tc, false);
			HITS.add(new int[] {x, y, w, h, i});
			x += w + 2;
		}
		if (tabs.get(active).name().equals("Private")) {
			String hint = partner == null ? "No one has messaged you yet" : "Replying to " + partner;
			g.drawString(font, hint, x + 4, y + 2, Theme.DIM, false);
		} else if (FactionsClient.CONFIG.chatRoute && "factions".equalsIgnoreCase(ServerLink.REALM)
				&& (tabs.get(active).name().equals("Faction") || tabs.get(active).name().equals("Ally"))) {
			g.drawString(font, "Typing to your " + tabs.get(active).name().toLowerCase(java.util.Locale.ROOT), x + 4, y + 2, Theme.DIM, false);
		}
	}

	public static boolean click(double mx, double my) {
		for (int[] r : HITS) {
			if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
				select(r[4]);
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.4f));
				return true;
			}
		}
		return false;
	}

	/** Chat closed: small badge above the chat when Faction/Ally/Private have new messages. */
	public static void renderUnread(GuiGraphics g, Font font) {
		if (!enabled() || unread.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen) return;
		int x = 2, y = g.guiHeight() - 27;
		for (Tab t : tabs) {
			int n = unread.getOrDefault(t.name(), 0);
			if (n == 0 || t.name().equals("Server")) continue;
			String label = "● " + t.name() + " " + Math.min(n, 99);
			int w = font.width(label) + 8;
			Theme.rect(g, x, y, w, 11, 0xB00B0716);
			g.drawString(font, label, x + 4, y + 2, t.color(), false);
			x += w + 2;
		}
	}
}
