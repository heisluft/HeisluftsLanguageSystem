package de.heisluft.lang;

import static de.heisluft.lang.HeisluftsLanguageSystem.LOG;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class LanguageManager {

	public static final LanguageManager INSTANCE = new LanguageManager();

	private final Map<Player, Locale> mappedLanguages = new HashMap<>();

	private LanguageManager() {}

	final Set<JavaPlugin> plugins = new HashSet<>();

	public void registerPlugin(JavaPlugin instance) {
		plugins.add(instance);
		initPlugin(instance);
		for(final Locale l : Locale.values()) {
			l.init(instance);
		}
	}

	private void initPlugin(JavaPlugin instance) {
		LOG.info("Loading Lang resources for Plugin " + instance.getName());
		new File(instance.getDataFolder() + "/lang").mkdirs();
	}

	void setLanguageForPlayer(Player player, String locale) {
		setLanguageForPlayer(player, Locale.byName(locale, Locale.EN_US));
	}
	
	void setLanguageForPlayer(Player p, Locale l) {
		mappedLanguages.put(p, l);
	}
	
	public String translate(String unlocalized, Player translatedTo, Object... args) {
		return translate(unlocalized, getLanguageForPlayer(translatedTo), args);
	}
	
	public String translate(String unlocalized, Locale language, Object... args) {
		return language.translate(unlocalized, args);
	}
	
	public Locale getLanguageForPlayer(Player p) {
		return mappedLanguages.get(p);
	}

	public String translate(String unlocalized, String language, Object... args) {
		return translate(unlocalized, Locale.byName(language, Locale.EN_US), args);
	}

	public void unmapPlayerLanguage(Player player) {
		mappedLanguages.remove(player);
	}
}
