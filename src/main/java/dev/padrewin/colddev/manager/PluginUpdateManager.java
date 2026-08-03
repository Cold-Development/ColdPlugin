package dev.padrewin.colddev.manager;

import dev.padrewin.colddev.ColdPlugin;
import dev.padrewin.colddev.config.CommentedFileConfiguration;
import dev.padrewin.colddev.utils.ColdDevUtils;
import dev.padrewin.colddev.utils.StringPlaceholders;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class PluginUpdateManager extends Manager implements Listener {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_PURPLE_CHINESE = "\u001B[38;5;93m";

    private static final String[] SNAPSHOT_HEADER = {
            "================================================",
            " You are currently running a DEVELOPMENT BUILD!",
            " These types of builds are not meant to be run",
            " on a production server, and are not supported.",
            "================================================"
    };

    private static final Map<String, Boolean> updateMessageShownMap = new HashMap<>();

    private String updateVersion;

    public PluginUpdateManager(ColdPlugin coldPlugin) {
        super(coldPlugin);
        Bukkit.getPluginManager().registerEvents(this, this.coldPlugin);
    }

    @Override
    public void reload() {
        if (this.coldPlugin.getGithubOwner() == null || this.coldPlugin.getGithubRepo() == null || this.updateVersion != null)
            return;

        File configFile = new File(this.coldPlugin.getColdDevDataFolder(), "config.yml");

        String currentVersion = this.coldPlugin.getDescription().getVersion();
        if (currentVersion.contains("-SNAPSHOT") && !hasShownUpdateMessage()) {
            for (String line : SNAPSHOT_HEADER) {
                this.coldPlugin.getLogger().warning(line);
            }
            setUpdateMessageShown();
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

                if (hasShownUpdateMessage()) {
                    return;
                }

                String message = ANSI_RED + "An update for " + ANSI_PURPLE_CHINESE + this.coldPlugin.getName() + ANSI_RED + ANSI_BOLD
                        + " (" + this.updateVersion + ")" + ANSI_RESET + ANSI_RED + " is available! You are running "
                        + ANSI_BOLD + ANSI_RED + "v" + currentVersion + "." + ANSI_RESET;

                ColdDevUtils.getLogger().info(message);
                setUpdateMessageShown();
            }
        } catch (Exception e) {
            ColdDevUtils.getLogger().warning("An error occurred checking for an update. There is either no established internet connection or the GitHub API is down.");
        }
    }

    @Override
    public void disable() {

        updateMessageShownMap.clear();
    }

    private boolean hasShownUpdateMessage() {
        return updateMessageShownMap.getOrDefault(this.coldPlugin.getName(), false);
    }

    private void setUpdateMessageShown() {
        updateMessageShownMap.put(this.coldPlugin.getName(), true);
    }

    /**
     * Gets the latest version of the plugin from the GitHub API
     *
     * @return the latest version of the plugin from GitHub
     * @throws IOException if a network error occurs
     */
    private String getLatestVersion() throws IOException {
        String owner = this.coldPlugin.getGithubOwner();
        String repo = this.coldPlugin.getGithubRepo();
        URL github = new URL("https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest");

        HttpURLConnection connection = (HttpURLConnection) github.openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            String jsonResponse = response.toString();
            String tag = "\"tag_name\":\"";
            int start = jsonResponse.indexOf(tag) + tag.length();
            int end = jsonResponse.indexOf("\"", start);

            if (start != -1 && end != -1) {
                return jsonResponse.substring(start, end);
            } else {
                throw new IOException("Could not parse version from GitHub API response");
            }
        } else {
            throw new IOException("Failed to fetch latest version. Response code: " + responseCode);
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

        if (this.updateVersion == null) {
            return;
        }

        if (!player.isOp()) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                String website = coldPlugin.getDescription().getWebsite();
                String updateMessage = "&cAn update for " + ColdDevUtils.GRADIENT +
                        coldPlugin.getName() + " &c(&4%new%&c) is available! You are running &4v%current%&c.";

                StringPlaceholders placeholders = StringPlaceholders.of("new", updateVersion, "current", coldPlugin.getDescription().getVersion());

                ColdDevUtils.sendMessage(player, updateMessage, placeholders);

                if (website != null) {
                    Component clickHereComponent = Component.text("Click here to update")
                            .color(NamedTextColor.GREEN)
                            .decorate(TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.openUrl(website))
                            .hoverEvent(HoverEvent.showText(Component.text("Click to open GitHub.")));

                    player.sendMessage(clickHereComponent);
                    player.sendMessage(Component.empty());
                }
            }
        }.runTaskLater(this.coldPlugin, 150L);
    }
}