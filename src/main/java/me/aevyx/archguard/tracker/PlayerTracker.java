package me.aevyx.archguard.tracker;

import me.aevyx.archguard.ArchGuard;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class PlayerTracker implements Listener {

    private final ArchGuard plugin;

    // UUID → PacketType → timestamps of recent packets (sliding window)
    private final Map<UUID, Map<String, Deque<Long>>> packetLog = new ConcurrentHashMap<>();

    // UUID → violation counts per packet type
    private final Map<UUID, Map<String, Integer>> violations = new ConcurrentHashMap<>();

    public PlayerTracker(ArchGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Records a packet and returns true if the player is flooding.
     * Uses a sliding window (last N milliseconds defined in config).
     */
    public boolean recordAndCheck(Player player, String packetType) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long windowMs = plugin.getGuardConfig().getWindowMs();
        int threshold = plugin.getGuardConfig().getThreshold(packetType);

        // Get or create the deque for this player + packet type
        Map<String, Deque<Long>> playerLog = packetLog.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        Deque<Long> timestamps = playerLog.computeIfAbsent(packetType, k -> new ConcurrentLinkedDeque<>());

        // Add current timestamp
        timestamps.addLast(now);

        // Remove timestamps outside the sliding window
        while (!timestamps.isEmpty() && (now - timestamps.peekFirst()) > windowMs) {
            timestamps.pollFirst();
        }

        // Check if count exceeds threshold
        if (timestamps.size() > threshold) {
            int count = addViolation(uuid, packetType);
            plugin.getLogger().warning(
                "[ArchGuard] " + player.getName() + " sent " + timestamps.size() +
                " " + packetType + " packets in " + windowMs + "ms (threshold: " + threshold + ") | Violations: " + count
            );
            return true;
        }

        return false;
    }

    public int addViolation(UUID uuid, String packetType) {
        Map<String, Integer> playerViolations = violations.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        return playerViolations.merge(packetType, 1, Integer::sum);
    }

    public int getTotalViolations(UUID uuid) {
        Map<String, Integer> v = violations.get(uuid);
        if (v == null) return 0;
        return v.values().stream().mapToInt(Integer::intValue).sum();
    }

    public void clearPlayer(UUID uuid) {
        packetLog.remove(uuid);
        violations.remove(uuid);
    }

    public void clearAll() {
        packetLog.clear();
        violations.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Keep violation counts but clear packet log to free memory
        packetLog.remove(event.getPlayer().getUniqueId());
    }
}
