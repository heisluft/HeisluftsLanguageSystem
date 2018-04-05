package de.heisluft.lang.tp;


import de.heisluft.lang.tp.Reflection;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import de.heisluft.lang.tp.Reflection.FieldAccessor;
import de.heisluft.lang.tp.Reflection.MethodInvoker;

import com.google.common.collect.Lists;
import com.google.common.collect.MapMaker;
import com.mojang.authlib.GameProfile;

/**
 * Shortened from
 * <a href="https://github.com/dmulloy2/ProtocolLib/blob/master/modules/TinyProtocol/src/main/java/com/comphenix/tinyprotocol/TinyProtocol.java">Github</a>
 */
public class ProtocolInterceptor {
	private static final AtomicInteger ID = new AtomicInteger(0);

	// Used in order to lookup a channel
	private static final MethodInvoker getPlayerHandle = Reflection.getMethod("{obc}.entity.CraftPlayer", "getHandle");
	private static final FieldAccessor<Object> getConnection = Reflection.getField("{nms}.EntityPlayer", "playerConnection", Object.class);
	private static final FieldAccessor<Object> getManager = Reflection.getField("{nms}.PlayerConnection", "networkManager", Object.class);
	private static final FieldAccessor<Channel> getChannel = Reflection.getField("{nms}.NetworkManager", Channel.class, 0);

	// Looking up ServerConnection
	private static final Class<Object> minecraftServerClass = Reflection.getUntypedClass("{nms}.MinecraftServer");
	private static final Class<Object> serverConnectionClass = Reflection.getUntypedClass("{nms}.ServerConnection");
	private static final FieldAccessor<Object> getMinecraftServer = Reflection.getField("{obc}.CraftServer", minecraftServerClass, 0);
	private static final FieldAccessor<Object> getServerConnection = Reflection.getField(minecraftServerClass, serverConnectionClass, 0);
	private static final MethodInvoker getNetworkMarkers = Reflection.getTypedMethod(serverConnectionClass, null, List.class, serverConnectionClass);

	// Packets we have to intercept
	private static final Class<?> PACKET_LOGIN_IN_START = Reflection.getMinecraftClass("PacketLoginInStart");
	private static final FieldAccessor<GameProfile> getGameProfile = Reflection.getField(PACKET_LOGIN_IN_START, GameProfile.class, 0);

	// Speedup channel lookup
	private Map<String, Channel> channelLookup = new MapMaker().weakValues().makeMap();
	private Listener listener;

	// Channels that have already been removed
	private Set<Channel> uninjectedChannels = Collections.newSetFromMap(new MapMaker().weakKeys().<Channel, Boolean>makeMap());

	// List of network markers
	private List<Object> networkManagers;

	// Injected channel handlers
	private List<Channel> serverChannels = Lists.newArrayList();
	private ChannelInboundHandlerAdapter serverChannelHandler;
	private ChannelInitializer<Channel> beginInitProtocol;
	private ChannelInitializer<Channel> endInitProtocol;

	// Current handler name
	private String handlerName;

	private volatile boolean closed;
	private Plugin plugin;

	/**
	 * Construct a new instance of TinyProtocol, and start intercepting packets for all connected clients and future clients.
	 * <p>
	 * You can construct multiple instances per plugin.
	 *
	 * @param plugin - the plugin.
	 */
	protected ProtocolInterceptor(final Plugin plugin) {
		this.plugin = plugin;

		// Compute handler name
		this.handlerName = getHandlerName();

		// Prepare existing players
		registerBukkitEvents();

		try {
			registerChannelHandler();
			registerPlayers(plugin);
		} catch (IllegalArgumentException ex) {
			// Damn you, late bind
			plugin.getLogger().info("[TinyProtocol] Delaying server channel injection due to late bind.");

			new BukkitRunnable() {
				@Override
				public void run() {
					registerChannelHandler();
					registerPlayers(plugin);
					plugin.getLogger().info("[TinyProtocol] Late bind injection successful.");
				}
			}.runTask(plugin);
		}
	}

	private void createServerChannelHandler() {
		// Handle connected channels
		endInitProtocol = new ChannelInitializer<Channel>() {

			@Override
			protected void initChannel(Channel channel) {
				try {
					// This can take a while, so we need to stop the main thread from interfering
					synchronized (networkManagers) {
						// Stop injecting channels
						if (!closed) {
							channel.eventLoop().submit(() -> injectChannelInternal(channel));
						}
					}
				} catch (Exception e) {
					plugin.getLogger().log(Level.SEVERE, "Cannot inject incomming channel " + channel, e);
				}
			}

		};

		// This is executed before Minecraft's channel handler
		beginInitProtocol = new ChannelInitializer<Channel>() {

			@Override
			protected void initChannel(Channel channel) {
				channel.pipeline().addLast(endInitProtocol);
			}

		};

		serverChannelHandler = new ChannelInboundHandlerAdapter() {

			@Override
			public void channelRead(ChannelHandlerContext ctx, Object msg) {
				Channel channel = (Channel) msg;

				// Prepare to initialize ths channel
				channel.pipeline().addFirst(beginInitProtocol);
				ctx.fireChannelRead(msg);
			}

		};
	}

	/**
	 * Register bukkit events.
	 */
	private void registerBukkitEvents() {
		listener = new Listener() {

			@EventHandler(priority = EventPriority.LOWEST)
			public final void onPlayerLogin(PlayerLoginEvent e) {
				if (closed)
					return;

				Channel channel = getChannel(e.getPlayer());

				// Don't inject players that have been explicitly uninjected
				if (!uninjectedChannels.contains(channel)) {
					injectPlayer(e.getPlayer());
				}
			}

			@EventHandler
			public final void onPluginDisable(PluginDisableEvent e) {
				if (e.getPlugin().equals(plugin)) {
					close();
				}
			}

		};

		plugin.getServer().getPluginManager().registerEvents(listener, plugin);
	}

	@SuppressWarnings("unchecked")
	private void registerChannelHandler() {
		Object mcServer = getMinecraftServer.get(Bukkit.getServer());
		Object serverConnection = getServerConnection.get(mcServer);
		boolean looking = true;

		// We need to synchronize against this list
		networkManagers = (List<Object>) getNetworkMarkers.invoke(null, serverConnection);
		createServerChannelHandler();

		// Find the correct list, or implicitly throw an exception
		for (int i = 0; looking; i++) {
			List<Object> list = Reflection.getField(serverConnection.getClass(), List.class, i).get(serverConnection);

			for (Object item : list) {
				if (!ChannelFuture.class.isInstance(item))
					break;

				// Channel future that contains the server connection
				Channel serverChannel = ((ChannelFuture) item).channel();

				serverChannels.add(serverChannel);
				serverChannel.pipeline().addFirst(serverChannelHandler);
				looking = false;
			}
		}
	}

	private void unregisterChannelHandler() {
		if (serverChannelHandler == null)
			return;

		for (Channel serverChannel : serverChannels) {
			final ChannelPipeline pipeline = serverChannel.pipeline();

			// Remove channel handler
			serverChannel.eventLoop().execute(() -> {
				try {
					pipeline.remove(serverChannelHandler);
				} catch (NoSuchElementException e) {
					// That's fine
				}
			});
		}
	}

	private void registerPlayers(Plugin plugin) {
		for (Player player : plugin.getServer().getOnlinePlayers()) {
			injectPlayer(player);
		}
	}

	/**
	 * Invoked when the server has received a packet from a given player.
	 * <p>
	 * Use {@link Channel#remoteAddress()} to get the remote address of the client.
	 *
	 * @param sender - the player that sent the packet, NULL for early login/status packets.
	 * @param channel - channel that received the packet. Never NULL.
	 * @param packet - the packet being received.
	 * @return The packet to recieve instead, or NULL to cancel.
	 */
	public Object onPacketInAsync(Player sender, Channel channel, Object packet) {
		return packet;
	}

	/**
	 * Retrieve the name of the channel injector, default implementation is "tiny-" + plugin name + "-" + a unique ID.
	 * <p>
	 * Note that this method will only be invoked once. It is no longer necessary to override this to support multiple instances.
	 *
	 * @return A unique channel handler name.
	 */
	private String getHandlerName() {
		return "tiny-" + plugin.getName() + "-" + ID.incrementAndGet();
	}

	/**
	 * Add a custom channel handler to the given player's channel pipeline, allowing us to intercept sent and received packets.
	 * <p>
	 * This will automatically be called when a player has logged in.
	 *
	 * @param player - the player to inject.
	 */
	private void injectPlayer(Player player) {
		injectChannelInternal(getChannel(player)).player = player;
	}

	/**
	 * Add a custom channel handler to the given channel.
	 *
	 * @param channel - the channel to inject.
	 * @return The packet interceptor.
	 */
	private PacketInterceptor injectChannelInternal(Channel channel) {
		try {
			PacketInterceptor interceptor = (PacketInterceptor) channel.pipeline().get(handlerName);

			// Inject our packet interceptor
			if (interceptor == null) {
				interceptor = new PacketInterceptor();
				channel.pipeline().addBefore("packet_handler", handlerName, interceptor);
				uninjectedChannels.remove(channel);
			}

			return interceptor;
		} catch (IllegalArgumentException e) {
			// Try again
			return (PacketInterceptor) channel.pipeline().get(handlerName);
		}
	}

	/**
	 * Retrieve the Netty channel associated with a player. This is cached.
	 *
	 * @param player - the player.
	 * @return The Netty channel.
	 */
	private Channel getChannel(Player player) {
		Channel channel = channelLookup.get(player.getName());

		// Lookup channel again
		if (channel == null) {
			Object connection = getConnection.get(getPlayerHandle.invoke(player));
			Object manager = getManager.get(connection);

			channelLookup.put(player.getName(), channel = getChannel.get(manager));
		}

		return channel;
	}

	/**
	 * Uninject a specific player.
	 *
	 * @param player - the injected player.
	 */
	private void uninjectPlayer(Player player) {
		uninjectChannel(getChannel(player));
	}

	/**
	 * Uninject a specific channel.
	 * <p>
	 * This will also disable the automatic channel injection that occurs when a player has properly logged in.
	 *
	 * @param channel - the injected channel.
	 */
	private void uninjectChannel(final Channel channel) {
		// No need to guard against this if we're closing
		if (!closed) {
			uninjectedChannels.add(channel);
		}

		// See ChannelInjector in ProtocolLib, line 590
		channel.eventLoop().execute(() -> channel.pipeline().remove(handlerName));
	}

	/**
	 * Cease listening for packets. This is called automatically when your plugin is disabled.
	 */
	private void close() {
		if (!closed) {
			closed = true;

			// Remove our handlers
			for (Player player : plugin.getServer().getOnlinePlayers()) {
				uninjectPlayer(player);
			}

			// Clean up Bukkit
			HandlerList.unregisterAll(listener);
			unregisterChannelHandler();
		}
	}

	/**
	 * Channel handler that is inserted into the player's channel pipeline, allowing us to intercept sent and received packets.
	 *
	 * @author Kristian
	 */
	private final class PacketInterceptor extends ChannelDuplexHandler {
		// Updated by the login event
		volatile Player player;

		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
			// Intercept channel
			final Channel channel = ctx.channel();
			handleLoginStart(channel, msg);

			try {
				msg = onPacketInAsync(player, channel, msg);
			} catch (Exception e) {
				plugin.getLogger().log(Level.SEVERE, "Error in onPacketInAsync().", e);
			}

			if (msg != null) {
				super.channelRead(ctx, msg);
			}
		}

		private void handleLoginStart(Channel channel, Object packet) {
			if (PACKET_LOGIN_IN_START.isInstance(packet)) {
				GameProfile profile = getGameProfile.get(packet);
				channelLookup.put(profile.getName(), channel);
			}
		}
	}
}