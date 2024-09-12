package dev.padrewin.coldplugin.manager;

import dev.padrewin.coldplugin.ColdPlugin;
import dev.padrewin.coldplugin.config.CommentedFileConfiguration;
import dev.padrewin.coldplugin.utils.NMSUtil;
import dev.padrewin.coldplugin.utils.ColdDevUtils;
import dev.padrewin.coldplugin.utils.StringPlaceholders;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PluginUpdateManager extends Manager implements Listener {

    private static final String[] SNAPSHOT_HEADER = {
            "================================================",
            " You are currently running a DEVELOPMENT BUILD!",
            " These types of builds are not meant to be run",
            " on a production server, and are not supported.",
            "================================================"
    };

    private boolean displayedSnapshotHeader;
    private String updateVersion;

    public PluginUpdateManager(ColdPlugin coldPlugin) {
        super(coldPlugin);

        Bukkit.getPluginManager().registerEvents(this, this.coldPlugin);
    }

    @Override
    public void reload() {
        if (this.coldPlugin.getSpigotId() == -1)
            return;

        File configFile = new File(this.coldPlugin.getColdDevDataFolder(), "config.yml");

        String currentVersion = this.coldPlugin.getDescription().getVersion();
        if (currentVersion.contains("-SNAPSHOT") && !this.displayedSnapshotHeader) {
            for (String line : SNAPSHOT_HEADER)
                this.coldPlugin.getLogger().warning(line);
            this.displayedSnapshotHeader = true;
            return;
        }

        boolean firstLoad = false;
        CommentedFileConfiguration configuration = CommentedFileConfiguration.loadConfiguration(configFile);
        if (!configuration.contains("check-updates")) {
            configuration.set("check-updates", true, "Should all plugins running ColdDev check for updates?", "ColdDev is a core library created by Cold Development");
            configuration.save(configFile);
            firstLoad = true;
        }

        if (firstLoad || !configuration.getBoolean("check-updates"))
            return;

        // Check for updates
        this.coldPlugin.getScheduler().runTaskAsync(() -> this.checkForUpdate(currentVersion));
    }

    private void checkForUpdate(String currentVersion) {
        try {
            String latestVersion = this.getLatestVersion();

            if (ColdDevUtils.isUpdateAvailable(latestVersion, currentVersion)) {
                this.updateVersion = latestVersion;
                ColdDevUtils.getLogger().info("An update for " + this.coldPlugin.getName() + " (v" + this.updateVersion + ") is available! You are running v" + currentVersion + ".");
            }
        } catch (Exception e) {
            ColdDevUtils.getLogger().warning("An error occurred checking for an update. There is either no established internet connection or the Spigot API is down.");
        }
    }

    @Override
    public void disable() {

    }

    /**
     * Gets the latest version of the plugin from the Spigot Web API
     *
     * @return the latest version of the plugin from Spigot
     * @throws IOException if a network error occurs
     */
    private String getLatestVersion() throws IOException {
        URL spigot = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + this.coldPlugin.getSpigotId());
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(spigot.openStream()))) {
            return reader.readLine();
        }
    }

    /**
     * @return the version of the latest update of this plugin, or null if there is none
     */
    public String getUpdateVersion() {
        return this.updateVersion;
    }

    /**
     * Called when a player joins and notifies ops if an update is available
     *
     * @param event The join event
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (this.updateVersion == null || !player.isOp())
            return;

        String website = this.coldPlugin.getDescription().getWebsite();
        String updateMessage = "&eAn update for " + ColdDevUtils.GRADIENT +
                this.coldPlugin.getName() + " &e(&b%new%&e) is available! You are running &b%current%&e." +
                (website != null ? " " + website : "");

        StringPlaceholders placeholders = StringPlaceholders.of("new", this.updateVersion, "current", this.coldPlugin.getDescription().getVersion());

        ColdDevUtils.sendMessage(player, updateMessage, placeholders);
    }

}