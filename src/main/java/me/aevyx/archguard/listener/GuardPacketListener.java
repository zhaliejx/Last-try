package me.aevyx.archguard.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import me.aevyx.archguard.ArchGuard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class GuardPacketListener {

    private final ArchGuard plugin;

    // All client→server packet types we want to monitor
    private static final List<PacketType> MONITORED_TYPES = Arrays.asList(
        PacketType.Play.Client.WINDOW_CLICK,       // Inventory clicks (main exploit)
        PacketType.Play.Client.FLYING,             // Movement
        PacketType.Play.Client.POSITION,
        PacketType.Play.Client.LOOK,
        PacketType.Play.Client.POSITION_LOOK,
        PacketType.Play.Client.ARM_ANIMATION,      // Arm swing
        PacketType.Play.Client.ENTITY_ACTION,      // Sneak/sprint
        PacketType.Play.Client.BLOCK_DIG,          // Breaking blocks
        PacketType.Play.Client.USE_ITEM,           // Using items
        PacketType.Play.Client.HELD_ITEM_SLOT,     // Hotbar slot
        PacketType.Play.Client.SET_CREATIVE_SLOT,  // Creative inventory
        PacketType.Play.Client.CHAT,               // Chat
        PacketType.Play.Client.TAB_COMPLETE        // Tab complete
    );

    public GuardPacketListener(ArchGuard plugin) {
        this.plugin = plugin;
    }

    public void register() {
        PacketType[] types = MONITORED_TYPES.toArray(new PacketType[0]);

        // Capture as ArchGuard specifically — PacketAdapter has its own protected
        // field named "plugin" typed as org.bukkit.plugin.Plugin which would shadow
        // the outer field and cause getGuardConfig()/getTracker() to not compile.
        final ArchGuard guard = this.plugin;

        ProtocolLibrary.getProtocolManager().addPacketListener(
            new PacketAdapter(guard, ListenerPriority.LOWEST, types) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    if (event.isCancelled()) return;

                    Player player = event.getPlayer();
                    if (player == null) return;

                    // Bypass for ops / players with bypass permission
                    if (player.hasPermission("archguard.bypass")) return;

                    String packetName = resolvePacketName(event.getPacketType());
                    if (!guard.getGuardConfig().isMonitored(packetName)) return;

                    // ── Specific exploit check: WINDOW_CLICK with invalid slot ──
                    // The YungLightUI exploit sets slot=120 (out of any valid inventory
                    // range) and spams 500-600 quick-move packets to crash the server.
                    // A legitimate client NEVER sends slot numbers this high.
                    if (event.getPacketType() == PacketType.Play.Client.WINDOW_CLICK) {
                        int slot = event.getPacket().getIntegers().read(1); // slot field
                        int maxValidSlot = 87; // largest possible inventory (9 hotbar + 27 inv + 27 chest + 4 craft + armor)
                        if (slot > maxValidSlot || slot < -1) {
                            event.setCancelled(true);
                            guard.getTracker().addViolation(player.getUniqueId(), "INVALID_SLOT");
                            int v = guard.getTracker().getTotalViolations(player.getUniqueId());
                            guard.getLogger().warning("[ArchGuard] " + player.getName() +
                                " sent WINDOW_CLICK with invalid slot " + slot + " | Violations: " + v);
                            if (guard.getGuardConfig().isAlertAdmins()) {
                                Bukkit.getScheduler().runTask(guard, () ->
                                    alertAdmins(player, "WINDOW_CLICK (invalid slot " + slot + ")", v)
                                );
                            }
                            if (v >= guard.getGuardConfig().getKickThreshold()) {
                                Bukkit.getScheduler().runTask(guard, () -> {
                                    if (!player.isOnline()) return;
                                    guard.getTracker().clearPlayer(player.getUniqueId());
                                    player.kickPlayer(
                                        "§c§lPacket Exploit Detected\n" +
                                        "§7You were sending invalid inventory packets.\n" +
                                        "§7If this is a mistake, please rejoin."
                                    );
                                });
                            }
                            return;
                        }
                    }

                    boolean flooding = guard.getTracker().recordAndCheck(player, packetName);
                    if (!flooding) return;

                    // Cancel the flooding packet if configured
                    if (guard.getGuardConfig().isCancelPackets()) {
                        event.setCancelled(true);
                    }

                    int totalViolations = guard.getTracker().getTotalViolations(player.getUniqueId());

                    // Alert admins (run on main thread to avoid async chat issues)
                    if (guard.getGuardConfig().isAlertAdmins()) {
                        Bukkit.getScheduler().runTask(guard, () ->
                            alertAdmins(player, packetName, totalViolations)
                        );
                    }

                    // Kick after repeated violations
                    int kickThreshold = guard.getGuardConfig().getKickThreshold();
                    if (totalViolations >= kickThreshold) {
                        Bukkit.getScheduler().runTask(guard, () -> {
                            if (!player.isOnline()) return;
                            guard.getLogger().warning("[ArchGuard] Kicking " + player.getName() +
                                " for packet flooding (" + totalViolations + " violations).");
                            guard.getTracker().clearPlayer(player.getUniqueId());
                            player.kickPlayer(
                                "§c§lPacket Flood Detected\n" +
                                "§7You were kicked for sending too many packets.\n" +
                                "§7If this is a mistake, please rejoin."
                            );
                        });
                    }
                }
            }
        );

        guard.getLogger().info("Registered packet listener for " + MONITORED_TYPES.size() + " packet types.");
    }

    private void alertAdmins(Player offender, String packetType, int violations) {
        String msg = "§c[ArchGuard] §e" + offender.getName() +
                     " §7is flooding §e" + packetType +
                     " §7packets. §cViolations: " + violations;

        Bukkit.getOnlinePlayers().stream()
            .filter(p -> p.hasPermission("archguard.admin"))
            .forEach(p -> p.sendMessage(msg));
    }

    /**
     * Maps a PacketType to a clean config-friendly name.
     */
    private String resolvePacketName(PacketType type) {
        if (type == PacketType.Play.Client.WINDOW_CLICK)      return "WINDOW_CLICK";
        if (type == PacketType.Play.Client.FLYING)            return "FLYING";
        if (type == PacketType.Play.Client.POSITION)          return "POSITION";
        if (type == PacketType.Play.Client.LOOK)              return "LOOK";
        if (type == PacketType.Play.Client.POSITION_LOOK)     return "POSITION_LOOK";
        if (type == PacketType.Play.Client.ARM_ANIMATION)     return "ARM_ANIMATION";
        if (type == PacketType.Play.Client.ENTITY_ACTION)     return "ENTITY_ACTION";
        if (type == PacketType.Play.Client.BLOCK_DIG)         return "BLOCK_DIG";
        if (type == PacketType.Play.Client.USE_ITEM)          return "USE_ITEM";
        if (type == PacketType.Play.Client.HELD_ITEM_SLOT)    return "HELD_ITEM_SLOT";
        if (type == PacketType.Play.Client.SET_CREATIVE_SLOT) return "CREATIVE";
        if (type == PacketType.Play.Client.CHAT)              return "CHAT";
        if (type == PacketType.Play.Client.TAB_COMPLETE)      return "TAB_COMPLETE";
        return type.name();
    }
}
