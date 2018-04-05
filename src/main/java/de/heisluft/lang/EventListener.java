package de.heisluft.lang;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class EventListener implements Listener {

	@EventHandler
	public void onLeave(PlayerQuitEvent event) {
		LanguageManager.INSTANCE.unmapPlayerLanguage(event.getPlayer());
	}
}
