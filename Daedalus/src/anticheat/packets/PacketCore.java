package anticheat.packets;

import java.util.HashSet;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.util.Vector;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.EnumWrappers.EntityUseAction;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;

import anticheat.Daedalus;
import anticheat.packets.events.PacketBlockPlacementEvent;
import anticheat.packets.events.PacketEntityActionEvent;
import anticheat.packets.events.PacketHeldItemChangeEvent;
import anticheat.packets.events.PacketKeepAliveEvent;
import anticheat.packets.events.PacketKillauraEvent;
import anticheat.packets.events.PacketPlayerEvent;
import anticheat.packets.events.PacketPlayerType;
import anticheat.packets.events.PacketReadVelocityEvent;
import anticheat.packets.events.PacketSwingArmEvent;
import anticheat.packets.events.PacketUseEntityEvent;

public class PacketCore {
	public Daedalus Daedalus;
	private HashSet<EntityType> enabled;
	private static final PacketType[] ENTITY_PACKETS = new PacketType[] { PacketType.Play.Server.SPAWN_ENTITY_LIVING,
			PacketType.Play.Server.NAMED_ENTITY_SPAWN, PacketType.Play.Server.ENTITY_METADATA };

	public PacketCore(Daedalus Daedalus) {
		super();
		this.Daedalus = Daedalus;
		enabled = new HashSet<EntityType>();
		enabled.add(EntityType.valueOf((String) "PLAYER"));

		ProtocolLibrary.getProtocolManager().addPacketListener((PacketListener) new PacketAdapter(this.Daedalus,
				new PacketType[] { PacketType.Play.Client.USE_ENTITY }) {
			public void onPacketReceiving(final PacketEvent event) {
				final PacketContainer packet = event.getPacket();
				final Player player = event.getPlayer();
				if (player == null) {
					return;
				}
				try {
					Object playEntity = getNMSClass("PacketPlayInUseEntity");
					String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
					if (version.contains("1_7")) {
						if (packet.getHandle() == playEntity) {
							if (playEntity.getClass().getMethod("c") == null) {
								return;
							}
						}
					} else {
						if (packet.getHandle() == playEntity) {
							if (playEntity.getClass().getMethod("a") == null) {
								return;
							}
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				EnumWrappers.EntityUseAction type;
				try {
					type = packet.getEntityUseActions().read(0);
				} catch (Exception ex) {
					return;
				}

				final int entityId = (int) packet.getIntegers().read(0);
				Entity entity = null;
				for (final Entity entityentity : player.getWorld().getEntities()) {
					if (entityentity.getEntityId() == entityId) {
						entity = entityentity;
					}
				}
				Bukkit.getServer().getPluginManager().callEvent((Event) new PacketUseEntityEvent(type, player, entity));
				if (type == EntityUseAction.ATTACK) {
					Bukkit.getServer().getPluginManager()
							.callEvent(new PacketKillauraEvent(player, PacketPlayerType.USE));
				}
			}
		});
		ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(Daedalus, ENTITY_PACKETS) {

			public void onPacketSending(PacketEvent event) {
				PacketContainer packet = event.getPacket();
				Entity e = (Entity) packet.getEntityModifier(event).read(0);
				if (e instanceof LivingEntity && enabled.contains((Object) e.getType())
						&& packet.getWatchableCollectionModifier().read(0) != null
						&& e.getUniqueId() != event.getPlayer().getUniqueId()) {
					packet = packet.deepClone();
					event.setPacket(packet);
					if (event.getPacket().getType() == PacketType.Play.Server.ENTITY_METADATA) {
						WrappedDataWatcher watcher = new WrappedDataWatcher(
								(List) packet.getWatchableCollectionModifier().read(0));
						this.processDataWatcher(watcher);
						packet.getWatchableCollectionModifier().write(0,
								(List<WrappedWatchableObject>) watcher.getWatchableObjects());
					}
				}
			}

			private void processDataWatcher(WrappedDataWatcher watcher) {
				if (watcher != null && watcher.getObject(6) != null && watcher.getFloat(6) != 0.0F) {
					watcher.setObject(6, 1.0f);
				}
			}
		});
		ProtocolLibrary.getProtocolManager().addPacketListener((PacketListener) new PacketAdapter(this.Daedalus,
				new PacketType[] { PacketType.Play.Client.POSITION_LOOK }) {
			public void onPacketReceiving(final PacketEvent event) {
				final Player player = event.getPlayer();
				if (player == null) {
					return;
				}
				Bukkit.getServer().getPluginManager().callEvent((Event) new PacketPlayerEvent(player,
						(double) event.getPacket().getDoubles().read(0),
						(double) event.getPacket().getDoubles().read(1),
						(double) event.getPacket().getDoubles().read(2), (float) event.getPacket().getFloat().read(0),
						(float) event.getPacket().getFloat().read(1), PacketPlayerType.POSLOOK));
			}
		});
		ProtocolLibrary.getProtocolManager().addPacketListener((PacketListener) new PacketAdapter(this.Daedalus,
				new PacketType[] { PacketType.Play.Server.ENTITY_VELOCITY }) {
			public void onPacketSending(PacketEvent event) {
				 Player player = event.getPlayer();
				PacketContainer packet = event.getPacket();
				if (player == null) {
					return;
				}
				double x = packet.getIntegers().read(1).doubleValue() / 8000.0;
				double y = packet.getIntegers().read(2).doubleValue() / 8000.0;
				double z = packet.getIntegers().read(3).doubleValue() / 8000.0;
				Vector vec = new Vector(x, y, z);
				Bukkit.getServer().getPluginManager().callEvent(new PacketReadVelocityEvent(player, vec));
			}
		});
		ProtocolLibrary.getProtocolManager().addPacketListener(
				(PacketListener) new PacketAdapter(this.Daedalus, new PacketType[] { PacketType.Play.Client.LOOK }) {
					public void onPacketReceiving(final PacketEvent event) {
						Player player = event.getPlayer();

						if (player == null) {
							return;
						}

						Bukkit.getServer().getPluginManager()
								.callEvent(new PacketPlayerEvent(player, event.getPacket().getDoubles().read(0),
										event.getPacket().getDoubles().read(1), event.getPacket().getDoubles().read(2),
										event.getPacket().getFloat().read(0), event.getPacket().getFloat().read(1),
										PacketPlayerType.POSLOOK));
					}
				});
		ProtocolLibrary.getProtocolManager().addPacketListener((PacketListener) new PacketAdapter(this.Daedalus,
				new PacketType[] { PacketType.Play.Client.POSITION }) {
			public void onPacketReceiving(final PacketEvent event) {
				final Player player = event.getPlayer();
				if (player == null) {
					return;
				}
				Bukkit.getServer().getPluginManager().callEvent(
						(Event) new PacketPlayerEvent(player, (double) event.getPacket().getDoubles().read(0),
								(double) event.getPacket().getDoubles().read(1),
								(double) event.getPacket().getDoubles().read(2), player.getLocation().getYaw(),
								player.getLocation().getPitch(), PacketPlayerType.POSITION));
			}
		});
		ProtocolLibrary.getProtocolManager().addPacketListener((PacketListener) new PacketAdapter(this.Daedalus,
				new PacketType[] { PacketType.Play.Client.ENTITY_ACTION }) {
			public void onPacketReceiving(final PacketEvent event) {
				final PacketContainer packet = event.getPacket();
				final Player player = event.getPlayer();
				if (player == null) {
					return;
				}
				Bukkit.getServer().getPluginManager()
						.callEvent((Event) new PacketEntityActionEvent(player, (int) packet.getIntegers().read(1)));
			}
		});
		ProtocolLibrary.getProtocolManager().addPacketListener((PacketListener) new PacketAdapter(this.Daedalus,
				new PacketType[] { PacketType.Play.Client.KEEP_ALIVE }) {
			public void onPacketReceiving(final PacketEvent event) {
				final Player player = event.getPlayer();
				if (player == null) {
					return;
				}
				Bukkit.getServer().getPluginManager().callEvent((Event) new PacketKeepAliveEvent(player));
			}
		});
		ProtocolLibrary.getProtocolManager().addPacketListener((PacketListener) new PacketAdapter(this.Daedalus,
				new PacketType[] { PacketType.Play.Client.ARM_ANIMATION }) {
			public void onPacketReceiving(final PacketEvent event) {
				final Player player = event.getPlayer();
				if (player == null) {
					return;
				}
				Bukkit.getServer().getPluginManager()
						.callEvent(new PacketKillauraEvent(player, PacketPlayerType.ARM_SWING));
				Bukkit.getServer().getPluginManager().callEvent((Event) new PacketSwingArmEvent(event, player));
			}
		});
		ProtocolLibrary.getProtocolManager().addPacketListener((PacketListener) new PacketAdapter(this.Daedalus,
				new PacketType[] { PacketType.Play.Client.HELD_ITEM_SLOT }) {
			public void onPacketReceiving(final PacketEvent event) {
				final Player player = event.getPlayer();
				if (player == null) {
					return;
				}
				Bukkit.getServer().getPluginManager().callEvent((Event) new PacketHeldItemChangeEvent(event, player));
			}
		});
		ProtocolLibrary.getProtocolManager().addPacketListener((PacketListener) new PacketAdapter(this.Daedalus,
				new PacketType[] { PacketType.Play.Client.BLOCK_PLACE }) {
			public void onPacketReceiving(final PacketEvent event) {
				final Player player = event.getPlayer();
				if (player == null) {
					return;
				}
				Bukkit.getServer().getPluginManager().callEvent((Event) new PacketBlockPlacementEvent(event, player));
			}
		});
		ProtocolLibrary.getProtocolManager().addPacketListener(
				(PacketListener) new PacketAdapter(this.Daedalus, new PacketType[] { PacketType.Play.Client.FLYING }) {
					public void onPacketReceiving(final PacketEvent event) {
						final Player player = event.getPlayer();
						if (player == null) {
							return;
						}
						Bukkit.getServer().getPluginManager()
								.callEvent((Event) new PacketPlayerEvent(player, player.getLocation().getX(),
										player.getLocation().getY(), player.getLocation().getZ(),
										player.getLocation().getYaw(), player.getLocation().getPitch(),
										PacketPlayerType.FLYING));
					}
				});
	}

	public Class<?> getNMSClass(String name) {
		String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
		try {
			return Class.forName("net.minecraft.server." + version + "." + name);
		}

		catch (ClassNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}
}