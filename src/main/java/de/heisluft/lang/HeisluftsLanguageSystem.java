package de.heisluft.lang;

import de.heisluft.lang.tp.ProtocolInterceptor;
import de.heisluft.lang.tp.Reflection;
import io.netty.channel.Channel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class HeisluftsLanguageSystem extends JavaPlugin {


	private static HeisluftsLanguageSystem instance;
	static final Logger LOG = LogManager.getLogger("HeisluftsLangSystem");

	@Override
	public void onLoad() {
		instance = this;
	}

	@Override
	public void onEnable() {
		if(Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) ProtocolLibSupport.init();
		else {
			LOG.info("Using TinyProtocol as ProtocolLib is not present.");
			Reflection.FieldAccessor<String> langField = Reflection.getField("{nms}.PacketPlayInSettings", "a", String.class);
			new ProtocolInterceptor(this) {
				@Override
				public Object onPacketInAsync(Player sender, Channel channel, Object packet) {
					if(langField.hasField(packet)) LanguageManager.INSTANCE.setLanguageForPlayer(sender, langField.get(packet));
					return super.onPacketInAsync(sender, channel, packet);
				}
			};
		}

		Bukkit.getPluginManager().registerEvents(new EventListener(), this);
	}

	static HeisluftsLanguageSystem getInstance() {
		return instance;
	}
}