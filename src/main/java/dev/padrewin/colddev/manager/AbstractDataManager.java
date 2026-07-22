package dev.padrewin.colddev.manager;

import dev.padrewin.colddev.ColdPlugin;
import dev.padrewin.colddev.config.CommentedConfigurationSection;
import dev.padrewin.colddev.config.ColdConfig;
import dev.padrewin.colddev.config.ColdSetting;
import dev.padrewin.colddev.config.ColdSettingSerializers;
import dev.padrewin.colddev.database.DataMigration;
import dev.padrewin.colddev.database.DatabaseConnector;
import dev.padrewin.colddev.database.MySQLConnector;
import dev.padrewin.colddev.database.SQLiteConnector;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import dev.padrewin.colddev.utils.ColdDevUtils;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractDataManager extends Manager {

    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BOLD = "\u001B[1m";
    public static final String ANSI_ORANGE = "\u001B[38;5;214m";
    public static final String ANSI_LIGHT_BLUE = "\u001B[38;5;153m";
    public static final String ANSI_RED = "\u001B[31m";

    public static final class SettingKey {
        private static final List<ColdSetting<?>> KEYS = new ArrayList<>();

        public static final ColdSetting<CommentedConfigurationSection> MYSQL_SETTINGS = create(ColdSetting.ofSection("mysql-settings", "Settings for if you want to use MySQL for data management"));
        public static final ColdSetting<Boolean> MYSQL_SETTINGS_ENABLED = create(ColdSetting.of("mysql-settings.enabled", ColdSettingSerializers.BOOLEAN, false, "Enable MySQL", "If false, SQLite will be used instead"));
        public static final ColdSetting<String> MYSQL_SETTINGS_HOSTNAME = create(ColdSetting.of("mysql-settings.hostname", ColdSettingSerializers.STRING, "127.0.0.1", "MySQL Database Hostname"));
        public static final ColdSetting<Integer> MYSQL_SETTINGS_PORT = create(ColdSetting.of("mysql-settings.port", ColdSettingSerializers.INTEGER, 3306, "MySQL Database Port"));
        public static final ColdSetting<String> MYSQL_SETTINGS_DATABASE = create(ColdSetting.of("mysql-settings.database-name", ColdSettingSerializers.STRING, "", "MySQL Database Name"));
        public static final ColdSetting<String> MYSQL_SETTINGS_USERNAME = create(ColdSetting.of("mysql-settings.user-name", ColdSettingSerializers.STRING, "", "MySQL Database User Name"));
        public static final ColdSetting<String> MYSQL_SETTINGS_PASSWORD = create(ColdSetting.of("mysql-settings.user-password", ColdSettingSerializers.STRING, "", "MySQL Database User Password"));
        public static final ColdSetting<Boolean> MYSQL_SETTINGS_USE_SSL = create(ColdSetting.of("mysql-settings.use-ssl", ColdSettingSerializers.BOOLEAN, false, "If the database connection should use SSL", "You should enable this if your database supports SSL"));
        public static final ColdSetting<Integer> MYSQL_SETTINGS_POOL_SIZE = create(ColdSetting.of("mysql-settings.connection-pool-size", ColdSettingSerializers.INTEGER, 3, "The number of connections to make to the database"));

        private static <T> ColdSetting<T> create(ColdSetting<T> setting) {
            KEYS.add(setting);
            return setting;
        }

        public static List<ColdSetting<?>> getKeys() {
            return Collections.unmodifiableList(KEYS);
        }

        private SettingKey() {}
    }

    protected DatabaseConnector databaseConnector;
    private final AtomicBoolean reloadInProgress = new AtomicBoolean();

    public AbstractDataManager(ColdPlugin coldPlugin) {
        super(coldPlugin);
    }

    @Override
    public void reload() {
        this.reloadDatabase();
    }

    public final void reloadAsync(Runnable completionCallback) {
        if (!this.reloadInProgress.compareAndSet(false, true)) {
            return;
        }

        this.coldPlugin.getScheduler().runTaskAsync(() -> {
            boolean loaded = false;
            try {
                loaded = this.reloadDatabaseInternal();
            } finally {
                this.reloadInProgress.set(false);
            }

            if (loaded && completionCallback != null) {
                completionCallback.run();
            }
        });
    }

    private void reloadDatabase() {
        if (!this.reloadInProgress.compareAndSet(false, true)) {
            return;
        }

        try {
            this.reloadDatabaseInternal();
        } finally {
            this.reloadInProgress.set(false);
        }
    }

    private boolean reloadDatabaseInternal() {
        try {
            ColdConfig coldConfig = this.coldPlugin.getColdConfig();
            if (coldConfig.get(SettingKey.MYSQL_SETTINGS_ENABLED)) {
                String hostname = coldConfig.get(SettingKey.MYSQL_SETTINGS_HOSTNAME);
                int port = coldConfig.get(SettingKey.MYSQL_SETTINGS_PORT);
                String database = coldConfig.get(SettingKey.MYSQL_SETTINGS_DATABASE);
                String username = coldConfig.get(SettingKey.MYSQL_SETTINGS_USERNAME);
                String password = coldConfig.get(SettingKey.MYSQL_SETTINGS_PASSWORD);
                boolean useSSL = coldConfig.get(SettingKey.MYSQL_SETTINGS_USE_SSL);
                int poolSize = coldConfig.get(SettingKey.MYSQL_SETTINGS_POOL_SIZE);

                this.databaseConnector = new MySQLConnector(this.coldPlugin, hostname, port, database, username, password, useSSL, poolSize);
                this.coldPlugin.getLogger().info(ANSI_ORANGE + "Database connected using MySQL. " + ANSI_BOLD + ANSI_GREEN + "✔" + ANSI_RESET);
            } else {
                this.databaseConnector = new SQLiteConnector(this.coldPlugin);
                this.databaseConnector.cleanup();
                this.coldPlugin.getLogger().info(ANSI_LIGHT_BLUE + "Database connected using SQLite. " + ANSI_BOLD + ANSI_GREEN + "✔" + ANSI_RESET);
            }

            this.applyMigrations();
            return true;
        } catch (Exception ex) {
            this.coldPlugin.getLogger().severe("Fatal error trying to connect to database. Please make sure all your connection settings are correct and try again. Plugin has been disabled.");
            ex.printStackTrace();
            if (this.databaseConnector != null) {
                this.databaseConnector.closeConnection();
                this.databaseConnector = null;
            }
            Runnable disableTask = () -> Bukkit.getPluginManager().disablePlugin(this.coldPlugin);
            if (Bukkit.isPrimaryThread()) {
                disableTask.run();
            } else {
                this.coldPlugin.getScheduler().runTask(disableTask);
            }
        }

        return false;
    }

    @Override
    public void disable() {
        if (this.databaseConnector == null)
            return;

        // Wait for all database connections to finish
        long now = System.currentTimeMillis();
        long deadline = now + 5000; // Wait at most 5 seconds
        synchronized (this.databaseConnector.getLock()) {
            while (!this.databaseConnector.isFinished() && now < deadline) {
                try {
                    this.databaseConnector.getLock().wait(deadline - now);
                    now = System.currentTimeMillis();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        this.databaseConnector.closeConnection();
    }

    /**
     * @return true if the database connection is established, otherwise false
     */
    public final boolean isConnected() {
        return this.databaseConnector != null;
    }

    /**
     * @return The connector to the database
     */
    @NotNull
    public final DatabaseConnector getDatabaseConnector() {
        if (this.databaseConnector == null)
            throw new IllegalStateException("A database connection could not be established.");
        return this.databaseConnector;
    }

    /**
     * @return the prefix to be used by all table names
     */
    @NotNull
    public String getTablePrefix() {
        return this.coldPlugin.getDescription().getName().toLowerCase() + '_';
    }

    /**
     * @return all data migrations for the DataMigrationManager to handle
     */
    @NotNull
    public abstract List<Supplier<? extends DataMigration>> getDataMigrations();

    /**
     * Applies the DataMigrations defined by {@link #getDataMigrations()}.
     */
    private void applyMigrations() {
        List<DataMigration> migrations = this.getDataMigrations().stream()
                .map(Supplier::get)
                .collect(Collectors.toList());

        DatabaseConnector databaseConnector = this.getDatabaseConnector();
        databaseConnector.connect((connection -> {
            int currentMigration = -1;
            boolean migrationsExist;

            String query;
            if (databaseConnector instanceof SQLiteConnector) {
                query = "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?";
            } else {
                query = "SHOW TABLES LIKE ?";
            }

            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, this.getMigrationsTableName());
                migrationsExist = statement.executeQuery().next();
            }

            if (!migrationsExist) {
                // No migration table exists, create one
                String createTable = "CREATE TABLE " + this.getMigrationsTableName() + " (migration_version INT NOT NULL)";
                try (PreparedStatement statement = connection.prepareStatement(createTable)) {
                    statement.execute();
                }

                // Insert primary row into migration table
                String insertRow = "INSERT INTO " + this.getMigrationsTableName() + " VALUES (?)";
                try (PreparedStatement statement = connection.prepareStatement(insertRow)) {
                    statement.setInt(1, -1);
                    statement.execute();
                }
            } else {
                // Grab the current migration version
                String selectVersion = "SELECT migration_version FROM " + this.getMigrationsTableName();
                boolean badState = false;
                try (PreparedStatement statement = connection.prepareStatement(selectVersion)) {
                    ResultSet result = statement.executeQuery();
                    if (result.next()) {
                        currentMigration = result.getInt("migration_version");
                    } else {
                        badState = true;
                    }
                }

                if (badState) {
                    ColdDevUtils.getLogger().severe("Database migration table is missing the migration_version row! " +
                            "The database is currently in a bad state due to an unknown issue. Attempting to fix the migration " +
                            "column automatically... please contact the plugin developer for assistance if this does not work.");

                    // Find the highest migration version
                    currentMigration = migrations.stream()
                            .mapToInt(DataMigration::getRevision)
                            .max()
                            .orElse(-1);

                    // Insert a new row into the migration table, assume the highest migration is already applied in an
                    // attempt to prevent getting into an even worse state
                    String insertRow = "INSERT INTO " + this.getMigrationsTableName() + " VALUES (?)";
                    try (PreparedStatement statement = connection.prepareStatement(insertRow)) {
                        statement.setInt(1, currentMigration);
                        statement.execute();
                    }
                }
            }

            // Grab required migrations
            int finalCurrentMigration = currentMigration;
            List<DataMigration> requiredMigrations = migrations.stream()
                    .filter(x -> x.getRevision() > finalCurrentMigration)
                    .sorted(Comparator.comparingInt(DataMigration::getRevision))
                    .collect(Collectors.toList());

            // Nothing to migrate, abort
            if (requiredMigrations.isEmpty())
                return;

            // Migrate the data
            for (DataMigration dataMigration : requiredMigrations)
                dataMigration.migrate(databaseConnector, connection, this.getTablePrefix());

            // Set the new current migration to be the highest migrated to
            currentMigration = requiredMigrations
                    .stream()
                    .mapToInt(DataMigration::getRevision)
                    .max()
                    .orElse(-1);

            String updateVersion = "UPDATE " + this.getMigrationsTableName() + " SET migration_version = ?";
            try (PreparedStatement statement = connection.prepareStatement(updateVersion)) {
                statement.setInt(1, currentMigration);
                statement.execute();
            }
        }));
    }

    /**
     * @return the name of the migrations table
     */
    private String getMigrationsTableName() {
        return this.coldPlugin.getManager(AbstractDataManager.class).getTablePrefix() + "migrations";
    }

}
