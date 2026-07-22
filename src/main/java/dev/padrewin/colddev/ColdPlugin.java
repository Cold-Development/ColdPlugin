package dev.padrewin.colddev;

import dev.padrewin.colddev.command.framework.ColdCommandWrapper;
import dev.padrewin.colddev.command.rwd.RwdCommand;
import dev.padrewin.colddev.config.ColdConfig;
import dev.padrewin.colddev.config.ColdSetting;
import dev.padrewin.colddev.manager.AbstractCommandManager;
import dev.padrewin.colddev.manager.AbstractDataManager;
import dev.padrewin.colddev.manager.AbstractLocaleManager;
import dev.padrewin.colddev.manager.Manager;
import dev.padrewin.colddev.manager.PluginUpdateManager;
import dev.padrewin.colddev.objects.ColdPluginData;
import dev.padrewin.colddev.scheduler.ColdScheduler;
import dev.padrewin.colddev.utils.ColdDevUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public abstract class ColdPlugin extends JavaPlugin {

    public ColdPlugin() {
        this("defaultOwner", "defaultRepo", -1, null, null, null);
    }

    /**
     * The ColdPlugin identifier
     */
    public static final String COLDDEV_VERSION;

    static {
        String version = "unknown";
        Properties properties = new Properties();
        try (InputStream input = ColdPlugin.class.getResourceAsStream("/filter.properties")) {
            if (input != null) {
                properties.load(input);
                version = properties.getProperty("version", "unknown");
            }
        } catch (IOException e) {
            Bukkit.getLogger().severe("Failed to load version from filter.properties");
        }
        COLDDEV_VERSION = version;
    }

    /**
     * ANSI colors
     */
    String ANSI_GREEN = "\u001B[32m";
    String ANSI_RESET = "\u001B[0m";

    /**
     * The plugin ID on bStats
     */
    private final int bStatsId;

    /**
     * The GitHub owner and repository
     */
    private final String githubOwner;
    private final String githubRepo;

    /**
     * The classes that extend the abstract managers
     */
    private final Class<? extends AbstractDataManager> dataManagerClass;
    private final Class<? extends AbstractLocaleManager> localeManagerClass;
    private final Class<? extends AbstractCommandManager> commandManagerClass;

    /**
     * The plugin managers
     */
    private final Map<Class<? extends Manager>, Manager> managers;
    private final Deque<Class<? extends Manager>> managerInitializationStack;

    /**
     * The main config.yml
     */
    private ColdConfig coldConfig;

    private boolean firstInitialization = true;
    private boolean firstToRegister = false;

    public ColdPlugin(String githubOwner, String githubRepo, int bStatsId,
                      Class<? extends AbstractDataManager> dataManagerClass,
                      Class<? extends AbstractLocaleManager> localeManagerClass,
                      Class<? extends AbstractCommandManager> commandManagerClass) {
        super();

        this.githubOwner = githubOwner;
        this.githubRepo = githubRepo;
        this.bStatsId = bStatsId;
        this.dataManagerClass = dataManagerClass;
        this.localeManagerClass = localeManagerClass;
        this.commandManagerClass = commandManagerClass;

        this.managers = new ConcurrentHashMap<>();
        this.managerInitializationStack = new ConcurrentLinkedDeque<>();
    }

    public String getGithubOwner() {
        return this.githubOwner;
    }

    public String getGithubRepo() {
        return this.githubRepo;
    }

    @Override
    public void onLoad() {
        // Log that we are loading
        this.getLogger().info("Initializing using" + ANSI_GREEN + " ColdDev v" + COLDDEV_VERSION + ANSI_RESET);

        // Log severe if the library is not relocated
        if (!ColdDevUtils.isRelocated()) {
            ColdDevUtils.getLogger().severe("=====================================================");
            ColdDevUtils.getLogger().severe("DEVELOPER ERROR!!! ColdDev has not been relocated!");
            ColdDevUtils.getLogger().severe("=====================================================");
        }
    }

    /**
     * @return the data folder for ColdPlugin
     */
    @NotNull
    public final File getColdPluginDataFolder() {
        File configDir = new File(this.getDataFolder().getParentFile(), "ColdPlugin");
        if (!configDir.exists())
            configDir.mkdirs();
        return configDir;
    }

    @Override
    public void onEnable() {
        // bStats Metrics
        if (this.bStatsId != -1) {
            Metrics metrics = new Metrics(this, this.bStatsId);
            this.addCustomMetricsCharts(metrics);
        }

        // Inject the plugin class into the spigot services manager
        this.injectService();

        // Load the main config file
        this.getColdConfig();

        // Load managers
        this.reload(this::enable);
    }

    @Override
    public void onDisable() {
        // Run the plugin's disable code
        this.disable();

        // Shut down the managers
        this.disableManagers();
    }

    /**
     * Called during {@link JavaPlugin#onEnable} after managers have been enabled
     */
    protected abstract void enable();

    /**
     * Called during {@link JavaPlugin#onDisable} before managers have been disabled
     */
    protected abstract void disable();

    /**
     * @return the order in which Managers should be loaded, excluding
     */
    @NotNull
    protected List<Class<? extends Manager>> getManagerLoadPriority() {
        List<Class<? extends Manager>> managers = new ArrayList<>();

        if (this.hasDataManager()) {
            managers.add(this.dataManagerClass);
        }

        if (this.usesLocaleManager()) {
            managers.add(this.localeManagerClass);
        }

        if (this.hasCommandManager()) {
            managers.add(this.commandManagerClass);
        }

        return managers;
    }

    public boolean usesLocaleManager() {
        return this.localeManagerClass != null;
    }

    /**
     * Checks if the database is local only.
     * Returning true will prevent the database settings from appearing in the config.yml.
     *
     * @return true if the database is local only or doesn't exist, false otherwise
     */
    public boolean isLocalDatabaseOnly() {
        return false;
    }

    /**
     * @return the settings that should be written to the config.yml
     */
    @NotNull
    protected List<ColdSetting<?>> getColdConfigSettings() {
        return Collections.emptyList();
    }

    /**
     * @return the header to place at the top of the config.yml
     */
    @NotNull
    protected String[] getColdConfigHeader() {
        return new String[0];
    }

    /**
     * Registers any custom bStats Metrics charts for the plugin
     *
     * @param metrics The Metrics instance
     */
    protected void addCustomMetricsCharts(Metrics metrics) {
        // Must be overridden for any functionality.
    }

    /**
     * Reloads the plugin's managers
     */
    public void reload() {
        this.reload(null);
    }

    public void reload(Runnable completionCallback) {
        this.disableManagers();

        if (this.coldConfig != null)
            this.coldConfig.reload();

        this.reloadManagers(this.getReloadOrder(), completionCallback);
    }

    private void reloadManagers(List<Class<? extends Manager>> reloadOrder, Runnable completionCallback) {
        this.reloadManagers(reloadOrder, 0, completionCallback);
    }

    @SuppressWarnings("unchecked")
    private void reloadManagers(List<Class<? extends Manager>> reloadOrder, int index, Runnable completionCallback) {
        if (index >= reloadOrder.size()) {
            this.firstInitialization = false;
            if (completionCallback != null) {
                completionCallback.run();
            }
            return;
        }

        Class<? extends Manager> managerClass = reloadOrder.get(index);
        AtomicBoolean initialized = new AtomicBoolean();
        Manager manager = this.getOrCreateManager(managerClass, initialized);
        boolean shouldReload = initialized.get() || !this.firstInitialization;

        if (!shouldReload) {
            this.reloadManagers(reloadOrder, index + 1, completionCallback);
            return;
        }

        if (managerClass == this.dataManagerClass && manager instanceof AbstractDataManager dataManager) {
            dataManager.reloadAsync(() -> this.getScheduler().runTask(() -> {
                if (initialized.get()) {
                    this.managerInitializationStack.push(managerClass);
                }
                this.reloadManagers(reloadOrder, index + 1, completionCallback);
            }));
            return;
        }

        try {
            manager.reload();
        } catch (Exception e) {
            throw new ManagerLoadException(managerClass, e);
        }

        if (initialized.get()) {
            this.managerInitializationStack.push(managerClass);
        }

        this.reloadManagers(reloadOrder, index + 1, completionCallback);
    }

    private List<Class<? extends Manager>> getReloadOrder() {
        if (this.firstInitialization) {
            Set<Class<? extends Manager>> managerLoadPriority = new LinkedHashSet<>();

            if (this.hasDataManager())
                managerLoadPriority.add(this.dataManagerClass);

            if (this.usesLocaleManager())
                managerLoadPriority.add(this.localeManagerClass);

            if (this.hasCommandManager())
                managerLoadPriority.add(this.commandManagerClass);

            managerLoadPriority.addAll(this.getManagerLoadPriority());

            if (this.githubOwner != null && this.githubRepo != null)
                managerLoadPriority.add(PluginUpdateManager.class);

            return new ArrayList<>(managerLoadPriority);
        }

        List<Class<? extends Manager>> initStack = new ArrayList<>(this.managerInitializationStack);
        Collections.reverse(initStack);
        return initStack;
    }

    /**
     * Runs {@link Manager#disable} on all managers in the reverse order that they were loaded.
     */
    private void disableManagers() {
        for (Class<? extends Manager> managerClass : this.managerInitializationStack) {
            Manager manager = this.managers.get(managerClass);
            try {
                manager.disable();
            } catch (Exception e) {
                throw new ManagerUnloadException(managerClass, e);
            }
        }
    }

    /**
     * Gets a manager instance and loads it if this is the first call to get it.
     *
     * @param managerClass The class of the manager to get
     * @param <T> extends Manager
     * @return A new or existing instance of the given manager class
     * @throws ManagerLoadException if the manager fails to load
     * @throws ManagerInitializationException if the manager fails to initialize
     */
    @SuppressWarnings("unchecked")
    @NotNull
    public <T extends Manager> T getManager(Class<T> managerClass) {
        Class<? extends Manager> lookupClass = this.remapAbstractManagerClasses(managerClass);

        if (managerClass == AbstractLocaleManager.class && !this.usesLocaleManager()) {
            throw new ManagerInitializationException(managerClass, new NullPointerException("LocaleManager is not defined."));
        }

        AtomicBoolean initialized = new AtomicBoolean();
        T manager = (T) this.getOrCreateManager(lookupClass, initialized);

        if (initialized.get()) {
            try {
                manager.reload();
            } catch (Exception e) {
                throw new ManagerLoadException(lookupClass, e);
            } finally {
                this.managerInitializationStack.push(lookupClass);
            }
        }

        return manager;
    }

    private Manager getOrCreateManager(Class<? extends Manager> managerClass, AtomicBoolean initialized) {
        return this.managers.computeIfAbsent(managerClass, key -> {
            try {
                return managerClass.getConstructor(ColdPlugin.class).newInstance(this);
            } catch (Exception e) {
                throw new ManagerInitializationException(managerClass, e);
            } finally {
                initialized.set(true);
            }
        });
    }

    protected <T extends Manager> Class<? extends Manager> remapAbstractManagerClasses(Class<T> managerClass) {
        Class<? extends Manager> lookupClass;
        if (this.hasDataManager() && managerClass == AbstractDataManager.class) {
            lookupClass = this.dataManagerClass;
        } else if (this.usesLocaleManager() && managerClass == AbstractLocaleManager.class) {
            lookupClass = this.localeManagerClass;
        } else if (this.hasCommandManager() && managerClass == AbstractCommandManager.class) {
            lookupClass = this.commandManagerClass;
        } else {
            lookupClass = managerClass;
        }
        return lookupClass;
    }

    /**
     * @return the scheduler for this plugin
     */
    public final ColdScheduler getScheduler() {
        return ColdScheduler.getInstance(this);
    }

    /**
     * @return the main config for this plugin
     */
    public final ColdConfig getColdConfig() {
        if (this.coldConfig == null) {
            List<ColdSetting<?>> settings = new ArrayList<>();

            if (this.hasLocaleManager())
                settings.addAll(AbstractLocaleManager.SettingKey.getKeys());

            settings.addAll(this.getColdConfigSettings());

            if (this.hasDataManager() && !this.isLocalDatabaseOnly())
                settings.addAll(AbstractDataManager.SettingKey.getKeys());

            File file = new File(this.getDataFolder(), "config.yml");
            this.coldConfig = ColdConfig.builder(file)
                    .header(this.getColdConfigHeader())
                    .settings(settings)
                    .writeDefaultValueComments()
                    .build();
        }
        return this.coldConfig;
    }

    /**
     * @return the ID of the plugin on Spigot, or -1 if not tracked
     */
/*
    public final int getSpigotId() {
        return this.spigotId;
    }
*/

    /**
     * @return the ID of this plugin on bStats, or -1 if not tracked
     */
    public final int getBStatsId() {
        return this.bStatsId;
    }

    /**
     * @return true if this plugin is the first to register, false otherwise
     */
    public final boolean isFirstToRegister() {
        return this.firstToRegister;
    }

    private void injectService() {
        if (this.getLoadedColdPluginsData().isEmpty()) {
            this.firstToRegister = true;
            new ColdCommandWrapper("colddev", this.getColdDevDataFolder(), this, new RwdCommand(this)).register();
        }

        // Register our service
        Bukkit.getServicesManager().register(ColdPlugin.class, this, this, ServicePriority.Normal);
    }

    /**
     * @return data of all ColdPlugin installed on the server
     */
    @NotNull
    public final List<ColdPluginData> getLoadedColdPluginsData() {
        List<ColdPluginData> data = new ArrayList<>();

        ServicesManager servicesManager = Bukkit.getServicesManager();
        for (Class<?> service : servicesManager.getKnownServices()) {
            try {
                String coldDevVersion = (String) service.getField("COLDDEV_VERSION").get(null);
                Method updateVersionMethod = service.getMethod("getUpdateVersion");

                for (RegisteredServiceProvider<?> provider : servicesManager.getRegistrations(service)) {
                    Plugin plugin = provider.getPlugin();
                    String pluginName = plugin.getName();
                    String pluginVersion = plugin.getDescription().getVersion();
                    String website = plugin.getDescription().getWebsite();
                    String updateVersion = (String) updateVersionMethod.invoke(plugin);
                    data.add(new ColdPluginData(pluginName, pluginVersion, updateVersion, website, coldDevVersion));
                }
            } catch (ReflectiveOperationException | ClassCastException ignored) { }
        }

        return data;
    }

    /**
     * @return the data folder for ColdDev
     */
    @NotNull
    public final File getColdDevDataFolder() {
        File configDir = new File(this.getDataFolder().getParentFile(), "ColdDev");
        if (!configDir.exists())
            configDir.mkdirs();
        return configDir;
    }

    /**
     * @return the version of the latest update of this plugin, or null if there is none
     */
    @NotNull
    public final String getUpdateVersion() {
        return this.getManager(PluginUpdateManager.class).getUpdateVersion();
    }

    public final boolean hasDataManager() {
        return this.dataManagerClass != null;
    }

    public final boolean hasLocaleManager() {
        return this.localeManagerClass != null;
    }

    public final boolean hasCommandManager() {
        return this.commandManagerClass != null;
    }

    /**
     * An exception thrown when a Manager fails during {@link Manager#reload()}
     */
    private static class ManagerLoadException extends RuntimeException {

        public ManagerLoadException(Class<? extends Manager> managerClass, Throwable cause) {
            super("Failed to load " + managerClass.getSimpleName(), cause);
        }

    }

    /**
     * An exception thrown when a Manager fails during {@link Manager#disable()}
     */
    private static class ManagerUnloadException extends RuntimeException {

        public ManagerUnloadException(Class<? extends Manager> managerClass, Throwable cause) {
            super("Failed to unload " + managerClass.getSimpleName(), cause);
        }

    }

    /**
     * An exception thrown when a Manager can't be initialized
     */
    private static class ManagerInitializationException extends RuntimeException {

        public ManagerInitializationException(Class<? extends Manager> managerClass, Throwable cause) {
            super("Failed to initialize " + managerClass.getSimpleName(), cause);
        }

    }

}
