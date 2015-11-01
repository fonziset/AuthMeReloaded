package fr.xephi.authme;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import fr.xephi.authme.command.CommandHandler;
import org.apache.logging.log4j.LogManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.mcstats.Metrics;
import org.mcstats.Metrics.Graph;

import com.earth2me.essentials.Essentials;
import com.onarandombox.MultiverseCore.MultiverseCore;

import fr.xephi.authme.api.API;
import fr.xephi.authme.api.NewAPI;
import fr.xephi.authme.cache.auth.PlayerAuth;
import fr.xephi.authme.cache.auth.PlayerCache;
import fr.xephi.authme.cache.backup.JsonCache;
import fr.xephi.authme.cache.limbo.LimboCache;
import fr.xephi.authme.cache.limbo.LimboPlayer;
import fr.xephi.authme.commands.AdminCommand;
import fr.xephi.authme.commands.CaptchaCommand;
import fr.xephi.authme.commands.ChangePasswordCommand;
import fr.xephi.authme.commands.ConverterCommand;
import fr.xephi.authme.commands.EmailCommand;
import fr.xephi.authme.commands.LoginCommand;
import fr.xephi.authme.commands.LogoutCommand;
import fr.xephi.authme.commands.RegisterCommand;
import fr.xephi.authme.commands.UnregisterCommand;
import fr.xephi.authme.converter.Converter;
import fr.xephi.authme.converter.ForceFlatToSqlite;
import fr.xephi.authme.datasource.CacheDataSource;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.datasource.DatabaseCalls;
import fr.xephi.authme.datasource.FlatFile;
import fr.xephi.authme.datasource.MySQL;
import fr.xephi.authme.datasource.SQLite;
import fr.xephi.authme.datasource.SQLite_HIKARI;
import fr.xephi.authme.listener.AuthMeBlockListener;
import fr.xephi.authme.listener.AuthMeEntityListener;
import fr.xephi.authme.listener.AuthMeInventoryPacketAdapter;
import fr.xephi.authme.listener.AuthMePlayerListener;
import fr.xephi.authme.listener.AuthMePlayerListener16;
import fr.xephi.authme.listener.AuthMePlayerListener18;
import fr.xephi.authme.listener.AuthMeServerListener;
import fr.xephi.authme.modules.ModuleManager;
import fr.xephi.authme.plugin.manager.BungeeCordMessage;
import fr.xephi.authme.plugin.manager.EssSpawn;
import fr.xephi.authme.process.Management;
import fr.xephi.authme.settings.Messages;
import fr.xephi.authme.settings.OtherAccounts;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.Spawn;
import net.milkbowl.vault.permission.Permission;
import net.minelink.ctplus.CombatTagPlus;

public class AuthMe extends JavaPlugin {

    /** Defines the name of the plugin. */
    // TODO: Create a getter method for this constant, and make it private
    public static final String PLUGIN_NAME = "AuthMeReloaded";
    /** Defines the current AuthMeReloaded version name. */
    private static final String PLUGIN_VERSION_NAME = "5.1-SNAPSHOT";
    /** Defines the current AuthMeReloaded version code. */
    private static final int PLUGIN_VERSION_CODE = 100; // Increase this number by one when an update is released

    private static AuthMe authme;
    private static Server server;
    private Logger authmeLogger;

    // TODO: Move this to a better place! -- timvisee
    private CommandHandler commandHandler = null;

    public Management management;
    public NewAPI api;
    public SendMailSSL mail;
    private Settings settings;
    private Messages m;
    public DataManager dataManager;
    public DataSource database;
    private JsonCache playerBackup;
    public OtherAccounts otherAccounts;
    public Location essentialsSpawn;
    public boolean antibotMod = false;
    public boolean delayedAntiBot = true;

    // Hooks TODO: move into modules
    public Permission permission;
    public Essentials ess;
    public MultiverseCore multiverse;
    public CombatTagPlus combatTagPlus;
    public AuthMeInventoryPacketAdapter inventoryProtector;

    // Module manager
    private ModuleManager moduleManager;

    // TODO: Create Manager for fields below
    public ConcurrentHashMap<String, BukkitTask> sessions = new ConcurrentHashMap<>();
    public ConcurrentHashMap<String, Integer> captcha = new ConcurrentHashMap<>();
    public ConcurrentHashMap<String, String> cap = new ConcurrentHashMap<>();
    public ConcurrentHashMap<String, String> realIp = new ConcurrentHashMap<>();

    // In case we need to cache PlayerAuths, prevent connection before it's done
    private boolean canConnect = true;

    public boolean isCanConnect() {
		return canConnect;
	}

	public void setCanConnect(boolean canConnect) {
		this.canConnect = canConnect;
	}

	public static AuthMe getInstance() {
        return authme;
    }

    public Settings getSettings() {
        return settings;
    }

    public void setMessages(Messages m) {
        this.m = m;
    }

    public Messages getMessages() {
        return m;
    }

    @Override
    public void onEnable() {
        // Set the Instance
        server = getServer();
        authmeLogger = Logger.getLogger("AuthMe");
        authme = this;

        // Set up and initialize the command handler
        this.commandHandler = new CommandHandler(false);
        this.commandHandler.init();

        // TODO: split the plugin in more modules
        moduleManager = new ModuleManager(this);
        @SuppressWarnings("unused")
        int loaded = moduleManager.loadModules();

        // TODO: remove vault as hard dependency
        PluginManager pm = server.getPluginManager();

        // Setup the Logger
        if (authmeLogger == null)
            authmeLogger = this.getLogger();
        else authmeLogger.setParent(this.getLogger());

        // Load settings and custom configurations
        // TODO: new configuration style (more files)
        try {
            settings = new Settings(this);
            Settings.reload();
        } catch (Exception e) {
            ConsoleLogger.writeStackTrace(e);
            ConsoleLogger.showError("Can't load the configuration file... Something went wrong, to avoid security issues the server will shutdown!");
            server.shutdown();
            return;
        }

        // Setup otherAccounts file
        otherAccounts = OtherAccounts.getInstance();

        // Setup messages
        m = Messages.getInstance();

        // Start the metrics service
        try {
            Metrics metrics = new Metrics(this);
            Graph messagesLanguage = metrics.createGraph("Messages language");
            Graph databaseBackend = metrics.createGraph("Database backend");

            // Custom graphs
            if(Settings.messageFile.exists()) {
                messagesLanguage.addPlotter(new Metrics.Plotter(Settings.messagesLanguage) {
                    @Override
                    public int getValue() {
                            return 1;
                    }
                });
            }
            databaseBackend.addPlotter(new Metrics.Plotter(Settings.getDataSource.toString()) {
                @Override
                public int getValue() {
                        return 1;
                }
            });

            metrics.start();
            ConsoleLogger.info("Metrics started successfully!");
        } catch (Exception e) {
            // Failed to submit the metrics data
            ConsoleLogger.writeStackTrace(e);
            ConsoleLogger.showError("Can't start Metrics! The plugin will work anyway...");
        }

        // Set Console Filter
        if (Settings.removePassword) {
            ConsoleFilter filter = new ConsoleFilter();
            this.getLogger().setFilter(filter);
            Bukkit.getLogger().setFilter(filter);
            Logger.getLogger("Minecraft").setFilter(filter);
            authmeLogger.setFilter(filter);
            // Set Log4J Filter
            try {
                Class.forName("org.apache.logging.log4j.core.Filter");
                setLog4JFilter();
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                ConsoleLogger.info("You're using Minecraft 1.6.x or older, Log4J support will be disabled");
            }
        }

        // AntiBot delay
        if (Settings.enableAntiBot) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {

                @Override
                public void run() {
                    delayedAntiBot = false;
                }
            }, 2400);
        }

        // Download GeoIp.dat file
        Utils.checkGeoIP();

        // Load MailApi if needed
        if (!Settings.getmailAccount.isEmpty() && !Settings.getmailPassword.isEmpty()) {
            mail = new SendMailSSL(this);
        }

        // Find Permissions
        checkVault();

        // Check Combat Tag Plus Version
        checkCombatTagPlus();

        // Check Multiverse
        checkMultiverse();

        // Check Essentials
        checkEssentials();

        // Check if the protocollib is available. If so we could listen for
        // inventory protection
        checkProtocolLib();

        // Do backup on start if enabled
        if (Settings.isBackupActivated && Settings.isBackupOnStart) {
            // Do backup and check return value!
            if (new PerformBackup(this).doBackup()) {
                ConsoleLogger.info("Backup performed correctly");
            } else {
                ConsoleLogger.showError("Error while performing the backup!");
            }
        }

        // Connect to the database and setup tables
        try {
            setupDatabase();
        } catch (Exception e) {
            ConsoleLogger.writeStackTrace(e);
            ConsoleLogger.showError(e.getMessage());
            ConsoleLogger.showError("Fatal error occurred during database connection! Authme initialization ABORTED!");
            stopOrUnload();
            return;
        }

        // Setup the inventory backup
        playerBackup = new JsonCache();

        // Set the DataManager
        dataManager = new DataManager(this);

        // Setup the new API
        api = new NewAPI(this);
        // Setup the old deprecated API
        new API(this);

        // Setup Management
        management = new Management(this);

        // Bungeecord hook
        if (Settings.bungee) {
            Bukkit.getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
            Bukkit.getMessenger().registerIncomingPluginChannel(this, "BungeeCord", new BungeeCordMessage(this));
        }

        // Reload support hook
        if (database != null) {
            int playersOnline = Utils.getOnlinePlayers().size();
            if (playersOnline < 1) {
                database.purgeLogged();
            } else if (Settings.reloadSupport) {
                for (PlayerAuth auth : database.getLoggedPlayers()) {
                    if (auth == null)
                        continue;
                    auth.setLastLogin(new Date().getTime());
                    database.updateSession(auth);
                    PlayerCache.getInstance().addPlayer(auth);
                }
            }
        }

        // Register events
        pm.registerEvents(new AuthMePlayerListener(this), this);
        // Try to register 1.6 player listeners
        try {
            Class.forName("org.bukkit.event.player.PlayerEditBookEvent");
            pm.registerEvents(new AuthMePlayerListener16(this), this);
        } catch (ClassNotFoundException ignore) {
        }
        // Try to register 1.8 player listeners
        try {
            Class.forName("org.bukkit.event.player.PlayerInteractAtEntityEvent");
            pm.registerEvents(new AuthMePlayerListener18(this), this);
        } catch (ClassNotFoundException ignore) {
        }
        pm.registerEvents(new AuthMeBlockListener(this), this);
        pm.registerEvents(new AuthMeEntityListener(this), this);
        pm.registerEvents(new AuthMeServerListener(this), this);

        // TODO: This is moving to AuthMe.onCommand();
        // Register commands
        //getCommand("authme").setExecutor(new AdminCommand(this));
        //getCommand("register").setExecutor(new RegisterCommand(this));
        //getCommand("login").setExecutor(new LoginCommand(this));
        getCommand("changepassword").setExecutor(new ChangePasswordCommand(this));
        getCommand("logout").setExecutor(new LogoutCommand(this));
        getCommand("unregister").setExecutor(new UnregisterCommand(this));
        getCommand("email").setExecutor(new EmailCommand(this));
        getCommand("captcha").setExecutor(new CaptchaCommand(this));
        getCommand("converter").setExecutor(new ConverterCommand(this));

        // Purge on start if enabled
        autoPurge();

        // Start Email recall task if needed
        recallEmail();

        // Configuration Security Warnings
        if (!Settings.isForceSingleSessionEnabled) {
            ConsoleLogger.showError("WARNING!!! By disabling ForceSingleSession, your server protection is inadequate!");
        }
        if (Settings.getSessionTimeout == 0 && Settings.isSessionsEnabled) {
            ConsoleLogger.showError("WARNING!!! You set session timeout to 0, this may cause security issues!");
        }

        // Sponsor messages
        ConsoleLogger.info("AuthMe hooks perfectly with the VERYGAMES server hosting!");
        ConsoleLogger.info("Development builds are available on our jenkins, thanks to f14stelt.");
        ConsoleLogger.info("Do you want a good gameserver? Look at our sponsor GameHosting.it leader in Italy as Game Server Provider!");

        // Successful message
        ConsoleLogger.info("AuthMe " + this.getDescription().getVersion() + " correctly enabled!");
    }

    @Override
    public void onDisable() {
        // Save player data
        Collection<? extends Player> players = Utils.getOnlinePlayers();
        if (players != null) {
            for (Player player : players) {
                this.savePlayer(player);
            }
        }

        // Do backup on stop if enabled
        if (Settings.isBackupActivated && Settings.isBackupOnStop) {
            boolean Backup = new PerformBackup(this).doBackup();
            if (Backup)
                ConsoleLogger.info("Backup performed correctly.");
            else ConsoleLogger.showError("Error while performing the backup!");
        }

        // Unload modules
        moduleManager.unloadModules();

        // Close the database
        if (database != null) {
            database.close();
        }

        // Disabled correctly
        ConsoleLogger.info("AuthMe " + this.getDescription().getVersion() + " disabled!");
    }

    // Stop/unload the server/plugin as defined in the configuration
    public void stopOrUnload() {
        if (Settings.isStopEnabled) {
            ConsoleLogger.showError("THE SERVER IS GOING TO SHUTDOWN AS DEFINED IN THE CONFIGURATION!");
            server.shutdown();
        } else {
            server.getPluginManager().disablePlugin(AuthMe.getInstance());
        }
    }

    // Show the exception message and stop/unload the server/plugin as defined
    // in the configuration
    public void stopOrUnload(Exception e) {
        ConsoleLogger.showError(e.getMessage());
        stopOrUnload();
    }

    // Initialize and setup the database
    public void setupDatabase() throws Exception {
        if (database != null) database.close();
        // Backend MYSQL - FILE - SQLITE - SQLITEHIKARI
        boolean isSQLite = false;
        switch (Settings.getDataSource) {
            case FILE:
                database = new FlatFile();
                break;
            case MYSQL:
                database = new MySQL();
                break;
            case SQLITE:
                database = new SQLite();
                isSQLite = true;
                break;
            case SQLITEHIKARI:
                database = new SQLite_HIKARI();
                isSQLite = true;
                break;
        }

        if (isSQLite) {
            server.getScheduler().runTaskAsynchronously(this, new Runnable() {

                @Override
                public void run() {
                    int accounts = database.getAccountsRegistered();
                    if (accounts >= 4000)
                        ConsoleLogger.showError("YOU'RE USING THE SQLITE DATABASE WITH " + accounts + "+ ACCOUNTS, FOR BETTER PERFORMANCES, PLEASE UPGRADE TO MYSQL!!");
                }
            });
        }

        if (Settings.isCachingEnabled) {
            database = new CacheDataSource(this, database);
        } else {
            database = new DatabaseCalls(database);
        }

        if (Settings.getDataSource == DataSource.DataSourceType.FILE) {
            Converter converter = new ForceFlatToSqlite(database, this);
            server.getScheduler().runTaskAsynchronously(this, converter);
            ConsoleLogger.showError("FlatFile backend has been detected and is now deprecated, next time server starts up, it will be changed to SQLite... Conversion will be started Asynchronously, it will not drop down your performance !");
            ConsoleLogger.showError("If you want to keep FlatFile, set file again into config at backend, but this message and this change will appear again at the next restart");
        }
    }

    // Set the console filter to remove the passwords
    private void setLog4JFilter() {
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {

            @Override
            public void run() {
                org.apache.logging.log4j.core.Logger coreLogger = (org.apache.logging.log4j.core.Logger) LogManager.getRootLogger();
                coreLogger.addFilter(new Log4JFilter());
            }
        });
    }

    // Check the presence of the Vault plugin and a permissions provider
    public void checkVault() {
        if (server.getPluginManager().isPluginEnabled("Vault")) {
            RegisteredServiceProvider<Permission> permissionProvider = server.getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
            if (permissionProvider != null) {
                permission = permissionProvider.getProvider();
                ConsoleLogger.info("Vault detected, hooking with the " + permission.getName() + " permissions system...");
            } else {
                ConsoleLogger.showError("Vault detected, but I can't find any permissions plugin to hook with!");
            }
        } else {
            permission = null;
        }
    }

    // Get the Multiverse plugin
    public void checkMultiverse() {
        if (Settings.multiverse && server.getPluginManager().isPluginEnabled("Multiverse-Core")) {
            try {
                multiverse = (MultiverseCore) server.getPluginManager().getPlugin("Multiverse-Core");
                ConsoleLogger.info("Hooked correctly with Multiverse-Core");
            } catch (Exception | NoClassDefFoundError ignored) {
                multiverse = null;
            }
        } else {
            multiverse = null;
        }
    }

    // Get the Essentials plugin
    public void checkEssentials() {
        if (server.getPluginManager().isPluginEnabled("Essentials")) {
            try {
                ess = (Essentials) server.getPluginManager().getPlugin("Essentials");
                ConsoleLogger.info("Hooked correctly with Essentials");
            } catch (Exception | NoClassDefFoundError ingnored) {
                ess = null;
            }
        } else {
            ess = null;
        }
        if (server.getPluginManager().isPluginEnabled("EssentialsSpawn")) {
            try {
                essentialsSpawn = new EssSpawn().getLocation();
                ConsoleLogger.info("Hooked correctly with EssentialsSpawn");
            } catch (Exception e) {
                essentialsSpawn = null;
                ConsoleLogger.showError("Can't read the /plugins/Essentials/spawn.yml file!");
            }
        } else {
            essentialsSpawn = null;
        }
    }

    // Check the presence of CombatTag
    public void checkCombatTagPlus() {
        if (server.getPluginManager().isPluginEnabled("CombatTagPlus")) {
            try {
                combatTagPlus = (CombatTagPlus) server.getPluginManager().getPlugin("CombatTagPlus");
                ConsoleLogger.info("Hooked correctly with CombatTagPlus");
            } catch (Exception | NoClassDefFoundError ingnored) {
                combatTagPlus = null;
            }
        } else {
            combatTagPlus = null;
        }
    }

    // Check the presence of the ProtocolLib plugin
    public void checkProtocolLib() {
        if (Settings.protectInventoryBeforeLogInEnabled) {
            if (server.getPluginManager().isPluginEnabled("ProtocolLib")) {
                inventoryProtector = new AuthMeInventoryPacketAdapter(this);
                inventoryProtector.register();
            } else {
                ConsoleLogger.showError("WARNING!!! The protectInventory feature requires ProtocolLib! Disabling it...");
                Settings.protectInventoryBeforeLogInEnabled = false;
            }
        }
    }

    // Check if a player/command sender have a permission
    public boolean authmePermissible(Player player, String perm) {
        if (player.hasPermission(perm)) {
            return true;
        } else if (permission != null) {
            return permission.playerHas(player, perm);
        }
        return false;
    }

    public boolean authmePermissible(CommandSender sender, String perm) {
        if (sender.hasPermission(perm)) {
            return true;
        } else if (permission != null) {
            return permission.has(sender, perm);
        }
        return false;
    }

    // Save Player Data
    public void savePlayer(Player player) {
        if ((Utils.isNPC(player)) || (Utils.isUnrestricted(player))) {
            return;
        }
        String name = player.getName().toLowerCase();
        if (PlayerCache.getInstance().isAuthenticated(name) && !player.isDead() && Settings.isSaveQuitLocationEnabled) {
            final PlayerAuth auth = new PlayerAuth(player.getName().toLowerCase(), player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ(), player.getWorld().getName(), player.getName());
            database.updateQuitLoc(auth);
        }
        if (LimboCache.getInstance().hasLimboPlayer(name)) {
            LimboPlayer limbo = LimboCache.getInstance().getLimboPlayer(name);
            if (!Settings.noTeleport) {
                player.teleport(limbo.getLoc());
            }

            Utils.addNormal(player, limbo.getGroup());
            player.setOp(limbo.getOperator());
            limbo.getTimeoutTaskId().cancel();
            LimboCache.getInstance().deleteLimboPlayer(name);
            if (this.playerBackup.doesCacheExist(player)) {
                this.playerBackup.removeCache(player);
            }
        }
        PlayerCache.getInstance().removePlayer(name);
        player.saveData();
    }

    // Select the player to kick when a vip player join the server when full
    public Player generateKickPlayer(Collection<? extends Player> collection) {
        Player player = null;
        for (Player p : collection) {
            if (!(authmePermissible(p, "authme.vip"))) {
                player = p;
                break;
            }
        }
        return player;
    }

    // Purge inactive players from the database, as defined in the configuration
    private void autoPurge() {
        if (!Settings.usePurge) {
            return;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -(Settings.purgeDelay));
        long until = calendar.getTimeInMillis();
        List<String> cleared = database.autoPurgeDatabase(until);
        if (cleared == null) {
            return;
        }
        if (cleared.isEmpty()) {
            return;
        }
        ConsoleLogger.info("AutoPurging the Database: " + cleared.size() + " accounts removed!");
        if (Settings.purgeEssentialsFile && this.ess != null)
            dataManager.purgeEssentials(cleared); // name to UUID convertion
                                                  // needed with latest versions
        if (Settings.purgePlayerDat)
            dataManager.purgeDat(cleared); // name to UUID convertion needed
                                           // with latest versions of MC
        if (Settings.purgeLimitedCreative)
            dataManager.purgeLimitedCreative(cleared);
        if (Settings.purgeAntiXray)
            dataManager.purgeAntiXray(cleared); // IDK if it uses UUID or
                                                // names... (Actually it purges
                                                // only names!)
        if (Settings.purgePermissions)
            dataManager.purgePermissions(cleared, permission);
    }

    // Return the spawn location of a player
    public Location getSpawnLocation(Player player) {
        World world = player.getWorld();
        String[] spawnPriority = Settings.spawnPriority.split(",");
        Location spawnLoc = world.getSpawnLocation();
        for (int i = spawnPriority.length - 1; i >= 0; i--) {
            String s = spawnPriority[i];
            if (s.equalsIgnoreCase("default") && getDefaultSpawn(world) != null)
                spawnLoc = getDefaultSpawn(world);
            if (s.equalsIgnoreCase("multiverse") && getMultiverseSpawn(world) != null)
                spawnLoc = getMultiverseSpawn(world);
            if (s.equalsIgnoreCase("essentials") && getEssentialsSpawn() != null)
                spawnLoc = getEssentialsSpawn();
            if (s.equalsIgnoreCase("authme") && getAuthMeSpawn(player) != null)
                spawnLoc = getAuthMeSpawn(player);
        }
        if (spawnLoc == null) {
            spawnLoc = world.getSpawnLocation();
        }
        return spawnLoc;
    }

    // Return the default spawnpoint of a world
    private Location getDefaultSpawn(World world) {
        return world.getSpawnLocation();
    }

    // Return the multiverse spawnpoint of a world
    private Location getMultiverseSpawn(World world) {
        if (multiverse != null && Settings.multiverse) {
            try {
                return multiverse.getMVWorldManager().getMVWorld(world).getSpawnLocation();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    // Return the essentials spawnpoint
    private Location getEssentialsSpawn() {
        if (essentialsSpawn != null) {
            return essentialsSpawn;
        }
        return null;
    }

    // Return the authme soawnpoint
    private Location getAuthMeSpawn(Player player) {
        if ((!database.isAuthAvailable(player.getName().toLowerCase()) || !player.hasPlayedBefore()) && (Spawn.getInstance().getFirstSpawn() != null)) {
            return Spawn.getInstance().getFirstSpawn();
        }
        if (Spawn.getInstance().getSpawn() != null) {
            return Spawn.getInstance().getSpawn();
        }
        return player.getWorld().getSpawnLocation();
    }

    public void switchAntiBotMod(boolean mode) {
        this.antibotMod = mode;
        Settings.switchAntiBotMod(mode);
    }

    public boolean getAntiBotModMode() {
        return this.antibotMod;
    }

    private void recallEmail() {
        if (!Settings.recallEmail)
            return;
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {

            @Override
            public void run() {
                for (Player player : Utils.getOnlinePlayers()) {
                    if (player.isOnline()) {
                        String name = player.getName().toLowerCase();
                        if (database.isAuthAvailable(name))
                            if (PlayerCache.getInstance().isAuthenticated(name)) {
                                String email = database.getAuth(name).getEmail();
                                if (email == null || email.isEmpty() || email.equalsIgnoreCase("your@email.com"))
                                    m.send(player, "add_email");
                            }
                    }
                }
            }
        }, 1, 1200 * Settings.delayRecall);
    }

    public String replaceAllInfos(String message, Player player) {
        int playersOnline = Utils.getOnlinePlayers().size();
        message = message.replace("&", "\u00a7");
        message = message.replace("{PLAYER}", player.getName());
        message = message.replace("{ONLINE}", "" + playersOnline);
        message = message.replace("{MAXPLAYERS}", "" + server.getMaxPlayers());
        message = message.replace("{IP}", getIP(player));
        message = message.replace("{LOGINS}", "" + PlayerCache.getInstance().getLogged());
        message = message.replace("{WORLD}", player.getWorld().getName());
        message = message.replace("{SERVER}", server.getServerName());
        message = message.replace("{VERSION}", server.getBukkitVersion());
        message = message.replace("{COUNTRY}", Utils.getCountryName(getIP(player)));
        return message;
    }

    public String getIP(Player player) {
        String name = player.getName().toLowerCase();
        String ip = player.getAddress().getAddress().getHostAddress();
        if (Settings.bungee) {
            if (realIp.containsKey(name))
                ip = realIp.get(name);
        }
        if (Settings.checkVeryGames)
            if (getVeryGamesIP(player) != null)
                ip = getVeryGamesIP(player);
        return ip;
    }

    public boolean isLoggedIp(String name, String ip) {
        int count = 0;
        for (Player player : Utils.getOnlinePlayers()) {
            if (ip.equalsIgnoreCase(getIP(player)) && database.isLogged(player.getName().toLowerCase()) && !player.getName().equalsIgnoreCase(name))
                count++;
        }
        return count >= Settings.getMaxLoginPerIp;
    }

    public boolean hasJoinedIp(String name, String ip) {
        int count = 0;
        for (Player player : Utils.getOnlinePlayers()) {
            if (ip.equalsIgnoreCase(getIP(player)) && !player.getName().equalsIgnoreCase(name))
                count++;
        }
        return count >= Settings.getMaxJoinPerIp;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    /**
     * Get Player real IP through VeryGames method
     *
     * @param player
     *            player
     */
    @Deprecated
    public String getVeryGamesIP(Player player) {
        String realIP = player.getAddress().getAddress().getHostAddress();
        String sUrl = "http://monitor-1.verygames.net/api/?action=ipclean-real-ip&out=raw&ip=%IP%&port=%PORT%";
        sUrl = sUrl.replace("%IP%", player.getAddress().getAddress().getHostAddress()).replace("%PORT%", "" + player.getAddress().getPort());
        try {
            URL url = new URL(sUrl);
            URLConnection urlc = url.openConnection();
            BufferedReader in = new BufferedReader(new InputStreamReader(urlc.getInputStream()));
            String inputLine = in.readLine();
            if (inputLine != null && !inputLine.isEmpty() && !inputLine.equalsIgnoreCase("error") && !inputLine.contains("error")) {
                realIP = inputLine;
            }
        } catch (Exception ignored) {
        }
        return realIP;
    }

    @Deprecated
    public String getCountryCode(String ip) {
        return Utils.getCountryCode(ip);
    }

    @Deprecated
    public String getCountryName(String ip) {
        return Utils.getCountryName(ip);
    }

    /**
     * Get the command handler instance.
     *
     * @return Command handler.
     */
    public CommandHandler getCommandHandler() {
        return this.commandHandler;
    }

    /**
     * Handle Bukkit commands.
     *
     * @param sender The command sender (Bukkit).
     * @param cmd The command (Bukkit).
     * @param commandLabel The command label (Bukkit).
     * @param args The command arguments (Bukkit).
     *
     * @return True if the command was executed, false otherwise.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        // Get the command handler, and make sure it's valid
        CommandHandler commandHandler = this.getCommandHandler();
        if(commandHandler == null)
            return false;

        // Handle the command, return the result
        return commandHandler.onCommand(sender, cmd, commandLabel, args);
    }

    /**
     * Get the current installed AuthMeReloaded version name.
     *
     * @return The version name of the currently installed AuthMeReloaded instance.
     */
    public static String getVersionName() {
        return PLUGIN_VERSION_NAME;
    }

    /**
     * Get the current installed AuthMeReloaded version code.
     *
     * @return The version code of the currently installed AuthMeReloaded instance.
     */
    public static int getVersionCode() {
        return PLUGIN_VERSION_CODE;
    }
}
