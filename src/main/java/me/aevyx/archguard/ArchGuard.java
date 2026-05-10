package me.aevyx.archguard;

import me.aevyx.archguard.config.GuardConfig;
import me.aevyx.archguard.listener.GuardPacketListener;
import me.aevyx.archguard.tracker.PlayerTracker;
import org.bukkit.plugin.java.JavaPlugin;

public class ArchGuard extends JavaPlugin {

    private GuardConfig guardConfig;
    private PlayerTracker tracker;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.guardConfig = new GuardConfig(this);
        this.tracker = new PlayerTracker(this);

        if (getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            getLogger().severe("ProtocolLib is not installed! ArchGuard requires ProtocolLib.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        new GuardPacketListener(this).register();
        getServer().getPluginManager().registerEvents(tracker, this);

        getLogger().info("ArchGuard enabled - protecting against packet flooding.");
    }

    @Override
    public void onDisable() {
        if (tracker != null) tracker.clearAll();
        getLogger().info("ArchGuard disabled.");
    }

    public GuardConfig getGuardConfig() { return guardConfig; }
    public PlayerTracker getTracker() { return tracker; }
}
