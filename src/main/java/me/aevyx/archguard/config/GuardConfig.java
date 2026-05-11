package me.aevyx.archguard.config;

import me.aevyx.archguard.ArchGuard;

import java.util.HashMap;
import java.util.Map;

public class GuardConfig {

    private final ArchGuard plugin;

    // Default thresholds per packet type (packets per window)
    private static final Map<String, Integer> DEFAULTS = new HashMap<>();

    static {
        DEFAULTS.put("WINDOW_CLICK",     20);  // Inventory manipulation (main exploit)
        DEFAULTS.put("FLYING",          100);  // Movement base packet
        DEFAULTS.put("POSITION",         60);  // Position update
        DEFAULTS.put("LOOK",             60);  // Look update
        DEFAULTS.put("POSITION_LOOK",    60);  // Combined position+look
        DEFAULTS.put("ARM_ANIMATION",    30);  // Swing arm
        DEFAULTS.put("ENTITY_ACTION",    20);  // Sprint/sneak/etc
        DEFAULTS.put("BLOCK_DIG",        20);  // Breaking blocks
        DEFAULTS.put("USE_ITEM",         20);  // Using items
        DEFAULTS.put("HELD_ITEM_SLOT",   20);  // Hotbar switching
        DEFAULTS.put("CREATIVE",         15);  // Creative set slot
        DEFAULTS.put("CHAT",             10);  // Chat messages
        DEFAULTS.put("TAB_COMPLETE",      5);  // Tab complete
    }

    public GuardConfig(ArchGuard plugin) {
        this.plugin = plugin;
    }

    /** Time window in milliseconds to measure packet rate */
    public long getWindowMs() {
        return plugin.getConfig().getLong("window-ms", 1000L);
    }

    /** Number of violations before kicking */
    public int getKickThreshold() {
        return plugin.getConfig().getInt("kick-after-violations", 3);
    }

    /** Whether to notify admins on flood detection */
    public boolean isAlertAdmins() {
        return plugin.getConfig().getBoolean("alert-admins", true);
    }

    /** Whether to cancel the flooding packets (drop them) */
    public boolean isCancelPackets() {
        return plugin.getConfig().getBoolean("cancel-packets", true);
    }

    /** Get the packet threshold for a specific packet type */
    public int getThreshold(String packetType) {
        String key = "thresholds." + packetType;
        if (plugin.getConfig().contains(key)) {
            return plugin.getConfig().getInt(key);
        }
        return DEFAULTS.getOrDefault(packetType, 50);
    }

    /** Whether a specific packet type is monitored */
    public boolean isMonitored(String packetType) {
        return plugin.getConfig().getBoolean("monitor." + packetType, true);
    }

    // ── Public Shame ──────────────────────────────────────
    public boolean isPublicShameEnabled() {
        return plugin.getConfig().getBoolean("public-shame.enabled", true);
    }

    public String getShameMessage(String playerName) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&',
            plugin.getConfig().getString("public-shame.message",
                "&c&l{player} &7has been caught &cPacket Flooding &7our server and has been &4Eliminated.")
                .replace("{player}", playerName));
    }

    // ── Ban System ────────────────────────────────────────
    public boolean isBanSystemEnabled() {
        return plugin.getConfig().getBoolean("ban-system.enabled", true);
    }

    public int getKicksBeforeBan() {
        return plugin.getConfig().getInt("ban-system.kicks-before-ban", 4);
    }

    // ── Kick / Ban Messages ───────────────────────────────
    public String getKickMessage() {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&',
            String.join("\n", plugin.getConfig().getStringList("kick-message")));
    }

    public String getBanMessage() {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&',
            String.join("\n", plugin.getConfig().getStringList("ban-message")));
    }
}
