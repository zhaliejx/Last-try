package me.aevyx.archguard.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import me.aevyx.archguard.ArchGuard;
import org.bukkit.Bukkit;
import org.bukkit.BanList;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class GuardPacketListener {

    private final ArchGuard plugin;

    private static final List<PacketType> MONITORED_TYPES = Arrays.asList(
        PacketType.Play.Client.WINDOW_CLICK,
        PacketType.Play.Client.FLYING,
        PacketType.Play.Client.POSITION,
        PacketType.Play.Client.LOOK,
        PacketType.Play.Client.POSITION_LOOK,
        PacketType.Play.Client.ARM_ANIMATION,
        PacketType.Play.Client.ENTITY_ACTION,
        PacketType.Play.Client.BLOCK_DIG,
        PacketType.Play.Client.USE_ITEM,
        PacketType.Play.Client.HELD_ITEM_SLOT,
        PacketType.Play.Client.SET_CREATIVE_SLOT,
        PacketType.Play.Client.CHAT,
        PacketType.Play.Client.TAB_COMPLETE
    );

    public GuardPacketListener(ArchGuard plugin) {
        this.plugin = plugin;
    }

    public void register() {
        final ArchGuard guard = this.plugin;
        PacketType[] types = MONITORED_TYPES.toArray(new PacketType[0]);

        ProtocolLibrary.getProtocolManager().addPacketListener(
            new PacketAdapter(guard, ListenerPriority.LOWEST, types) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    if (event.isCancelled()) return;

                    Player player = event.getPlayer();
                    if (player == null) return;
                    if (player.hasPermission("archguard.bypass")) return;

                    String packetName = resolvePacketName(event.getPacketType());
                    if (!guard.getGuardConfig().isMonitored(packetName)) return;

                    // ── Invalid slot exploit check ──
                    if (event.getPacketType() == PacketType.Play.Client.WINDOW_CLICK) {
                        int slot = event.getPacket().getIntegers().read(1);
                        if (slot > 87 || slot < -1) {
                            event.setCancelled(true);
                            guard.getTracker().addViolation(player.getUniqueId(), "INVALID_SLOT");
                            int v = guard.getTracker().getTotalViolations(player.getUniqueId());
                            guard.getLogger().warning("[ArchGuard] " + player.getName() +
                                " sent WINDOW_CLICK with invalid slot " + slot + " | Violations: " + v);

                            if (guard.getGuardConfig().isAlertAdmins())
                                Bukkit.getScheduler().runTask(guard, () ->
                                    alertAdmins(player, "WINDOW_CLICK (invalid slot " + slot + ")", v));

                            if (v >= guard.getGuardConfig().getKickThreshold())
                                Bukkit.getScheduler().runTask(guard, () -> processKick(player, guard));

                            return;
                        }
                    }

                    // ── Rate limit check ──
                    boolean flooding = guard.getTracker().recordAndCheck(player, packetName);
                    if (!flooding) return;

                    if (guard.getGuardConfig().isCancelPackets()) event.setCancelled(true);

                    int totalViolations = guard.getTracker().getTotalViolations(player.getUniqueId());

                    if (guard.getGuardConfig().isAlertAdmins())
                        Bukkit.getScheduler().runTask(guard, () ->
                            alertAdmins(player, packetName, totalViolations));

                    if (totalViolations >= guard.getGuardConfig().getKickThreshold())
                        Bukkit.getScheduler().runTask(guard, () -> processKick(player, guard));
                }
            }
        );

        guard.getLogger().info("Registered packet listener for " + MONITORED_TYPES.size() + " packet types.");
    }

    /** Handles kick, public shame broadcast, and IP ban logic */
    private void processKick(Player player, ArchGuard guard) {
        if (!player.isOnline()) return;

        int kickCount = guard.getTracker().incrementAndGetKickCount(player.getUniqueId());
        guard.getTracker().clearPlayer(player.getUniqueId());

        // Public shame broadcast
        if (guard.getGuardConfig().isPublicShameEnabled()) {
            Bukkit.broadcastMessage(guard.getGuardConfig().getShameMessage(player.getName()));
        }

        boolean shouldBan = guard.getGuardConfig().isBanSystemEnabled()
            && kickCount >= guard.getGuardConfig().getKicksBeforeBan();

        if (shouldBan) {
            String ip = player.getAddress().getAddress().getHostAddress();
            Bukkit.getBanList(BanList.Type.IP).addBan(ip, "Packet Flooding - ArchGuard", null, "ArchGuard");
            guard.getLogger().warning("[ArchGuard] IP banned " + player.getName() +
                " (" + ip + ") after " + kickCount + " kicks.");
            player.kickPlayer(guard.getGuardConfig().getBanMessage());
        } else {
            guard.getLogger().warning("[ArchGuard] Kicking " + player.getName() +
                " (kick #" + kickCount + ")");
            player.kickPlayer(guard.getGuardConfig().getKickMessage());
        }
    }

    private void alertAdmins(Player offender, String packetType, int violations) {
        String msg = "§c[ArchGuard] §e" + offender.getName() +
                     " §7is flooding §e" + packetType +
                     " §7packets. §cViolations: " + violations;
        Bukkit.getOnlinePlayers().stream()
            .filter(p -> p.hasPermission("archguard.admin"))
            .forEach(p -> p.sendMessage(msg));
    }

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
