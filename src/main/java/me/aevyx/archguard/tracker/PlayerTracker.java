package me.aevyx.archguard.tracker;

import me.aevyx.archguard.ArchGuard;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class PlayerTracker implements Listener {

    private final ArchGuard plugin;
    private final Map<UUID, Map<String, Deque<Long>>> packetLog = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> violations = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> kickCounts = new ConcurrentHashMap<>();
    private final File kickFile;
    private final FileConfiguration kickConfig;

    public PlayerTracker(ArchGuard plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        this.kickFile = new File(plugin.getDataFolder(), "kick_counts.yml");
        this.kickConfig = YamlConfiguration.loadConfiguration(kickFile);
        loadKickCounts();
    }

    private void loadKickCounts() {
        var section = kickConfig.getConfigurationSection("kicks");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try { kickCounts.put(UUID.fromString(key), kickConfig.getInt("kicks." + key)); }
            catch (IllegalArgumentException ignored) {}
        }
        plugin.getLogger().info("Loaded kick counts for " + kickCounts.size() + " player(s).");
    }

    public void saveKickCounts() {
        for (var entry : kickCounts.entrySet())
            kickConfig.set("kicks." + entry.getKey(), entry.getValue());
        try { kickConfig.save(kickFile); }
        catch (IOException e) { plugin.getLogger().severe("Failed to save kick_counts.yml: " + e.getMessage()); }
    }

    public int incrementAndGetKickCount(UUID uuid) {
        int count = kickCounts.merge(uuid, 1, Integer::sum);
        saveKickCounts();
        return count;
    }

    public int getKickCount(UUID uuid) { return kickCounts.getOrDefault(uuid, 0); }

    public boolean recordAndCheck(org.bukkit.entity.Player player, String packetType) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long windowMs = plugin.getGuardConfig().getWindowMs();
        int threshold = plugin.getGuardConfig().getThreshold(packetType);

        Deque<Long> timestamps = packetLog
            .computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(packetType, k -> new ConcurrentLinkedDeque<>());

        timestamps.addLast(now);
        while (!timestamps.isEmpty() && (now - timestamps.peekFirst()) > windowMs)
            timestamps.pollFirst();

        if (timestamps.size() > threshold) {
            int count = addViolation(uuid, packetType);
            plugin.getLogger().warning("[ArchGuard] " + player.getName() + " sent " + timestamps.size() +
                " " + packetType + " packets in " + windowMs + "ms | Violations: " + count);
            return true;
        }
        return false;
    }

    public int addViolation(UUID uuid, String packetType) {
        return violations.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).merge(packetType, 1, Integer::sum);
    }

    public int getTotalViolations(UUID uuid) {
        var v = violations.get(uuid);
        return v == null ? 0 : v.values().stream().mapToInt(Integer::intValue).sum();
    }

    public void clearPlayer(UUID uuid) { packetLog.remove(uuid); violations.remove(uuid); }
    public void clearAll() { packetLog.clear(); violations.clear(); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { packetLog.remove(event.getPlayer().getUniqueId()); }
}
