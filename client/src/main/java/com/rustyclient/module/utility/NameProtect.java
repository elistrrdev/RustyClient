package com.rustyclient.module.utility;

import com.rustyclient.core.Category;
import com.rustyclient.core.Module;
import com.rustyclient.core.Modules;
import com.rustyclient.core.setting.BoolSetting;
import com.rustyclient.core.setting.TextSetting;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NameProtect extends Module {
	private static final String OTHERS = "Other players";
	private static final String SERVER = "Server";
	private static final int CACHE_LIMIT = 4096;

	private final TextSetting ownName = text("Your name shows as", GENERAL, "You", 24);
	private final BoolSetting hideOthers = bool("Hide other players' names", GENERAL, false);
	private final BoolSetting hideServer = bool("Hide server address", GENERAL, true);
	private final TextSetting otherName = text("Other players show as", OTHERS, "Player", 24);
	private final BoolSetting numbered = bool("Number other players", OTHERS, true);
	private final TextSetting serverName = text("Server address shows as", SERVER, "Hidden server", 32);

	private static volatile Pattern pattern;
	private static volatile Map<String, String> replacements = Map.of();
	private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
	private static final ThreadLocal<Boolean> BUSY = ThreadLocal.withInitial(() -> false);
	private final Map<String, Integer> numbers = new HashMap<>();
	private String signature = "";
	private int timer;

	public NameProtect() {
		super("Name Protect", "venetian-mask", Category.UTILITY, false);
		otherName.visibleWhen(hideOthers::on);
		numbered.visibleWhen(hideOthers::on);
		serverName.visibleWhen(hideServer::on);
	}

	public static String apply(String text) {
		Pattern current = pattern;
		if (current == null || text == null || text.isEmpty() || BUSY.get()) {
			return text;
		}
		String cached = CACHE.get(text);
		if (cached != null) {
			return cached;
		}
		BUSY.set(true);
		try {
			Matcher matcher = current.matcher(text);
			String result = text;
			if (matcher.find()) {
				Map<String, String> map = replacements;
				StringBuilder out = new StringBuilder(text.length() + 16);
				do {
					String replacement = map.get(matcher.group().toLowerCase(Locale.ROOT));
					matcher.appendReplacement(out, Matcher.quoteReplacement(replacement == null ? matcher.group() : replacement));
				} while (matcher.find());
				matcher.appendTail(out);
				result = out.toString();
			}
			if (CACHE.size() > CACHE_LIMIT) {
				CACHE.clear();
			}
			CACHE.put(text, result);
			return result;
		} finally {
			BUSY.set(false);
		}
	}

	public static boolean active() {
		return pattern != null;
	}

	@Override
	protected void onDisable() {
		clear();
	}

	private void clear() {
		pattern = null;
		replacements = Map.of();
		signature = "";
		CACHE.clear();
	}

	@Override
	public void alwaysTick() {
		if (!enabled()) {
			if (pattern != null) {
				clear();
			}
			return;
		}
		if (timer-- > 0) {
			return;
		}
		timer = 10;
		rebuild();
	}

	private void rebuild() {
		Map<String, String> map = new LinkedHashMap<>();
		String own = mc.getUser().getName();
		String shown = ownName.get().isBlank() ? "You" : ownName.get();
		if (own != null && !own.isEmpty()) {
			map.put(own.toLowerCase(Locale.ROOT), shown);
		}
		if (mc.player != null) {
			String profile = mc.player.getGameProfile().name();
			if (profile != null && !profile.isEmpty()) {
				map.put(profile.toLowerCase(Locale.ROOT), shown);
			}
		}
		if (hideOthers.on() && mc.getConnection() != null) {
			String base = otherName.get().isBlank() ? "Player" : otherName.get();
			for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
				String name = info.getProfile().name();
				if (name == null || name.isEmpty()) {
					continue;
				}
				String key = name.toLowerCase(Locale.ROOT);
				if (map.containsKey(key)) {
					continue;
				}
				String replacement = base;
				if (numbered.on()) {
					int n = numbers.computeIfAbsent(key, k -> numbers.size() + 1);
					replacement = base + " " + n;
				}
				map.put(key, replacement);
			}
		}
		if (hideServer.on()) {
			ServerData server = mc.getCurrentServer();
			if (server != null && server.ip != null && !server.ip.isBlank()) {
				String ip = server.ip.trim();
				String shownServer = serverName.get().isBlank() ? "Hidden server" : serverName.get();
				map.put(ip.toLowerCase(Locale.ROOT), shownServer);
				int colon = ip.lastIndexOf(':');
				if (colon > 0) {
					map.put(ip.substring(0, colon).toLowerCase(Locale.ROOT), shownServer);
				}
			}
		}
		StringBuilder sig = new StringBuilder();
		for (Map.Entry<String, String> e : map.entrySet()) {
			sig.append(e.getKey()).append('\u0000').append(e.getValue()).append('\u0001');
		}
		if (sig.toString().equals(signature)) {
			return;
		}
		signature = sig.toString();
		if (map.isEmpty()) {
			pattern = null;
			replacements = Map.of();
			CACHE.clear();
			return;
		}
		List<String> keys = new ArrayList<>(map.keySet());
		keys.sort((a, b) -> Integer.compare(b.length(), a.length()));
		StringBuilder regex = new StringBuilder("(?<![A-Za-z0-9_])(?:");
		for (int i = 0; i < keys.size(); i++) {
			if (i > 0) {
				regex.append('|');
			}
			regex.append(Pattern.quote(keys.get(i)));
		}
		regex.append(")(?![A-Za-z0-9_])");
		replacements = Map.copyOf(map);
		pattern = Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
		CACHE.clear();
	}

	public static NameProtect get() {
		return Modules.get(NameProtect.class);
	}
}
