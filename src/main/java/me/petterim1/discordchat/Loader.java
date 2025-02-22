package me.petterim1.discordchat;

import org.allaymc.api.command.Command;
import org.allaymc.api.command.CommandSender;
import org.allaymc.api.plugin.Plugin;
import org.allaymc.api.Server;
import org.allaymc.api.utils.config.Config;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
// import net.luckperms.api.LuckPerms;
// import net.luckperms.api.LuckPermsProvider;

import java.util.regex.Pattern;

public class Loader extends Plugin {

    static Loader instance;
    static Config config;
    static JDA jda;
    static String channelId;
    static String consoleChannelId;
    static boolean debug;
    static boolean queueMessages;
    static MessageQueue messageQueue;
    // private LuckPerms luckPerms;
    static final DiscordCommandSender discordCommandSender = new DiscordCommandSender();
    private static final PlayerListener playerListener = new PlayerListener();
    private static final DiscordListener discordListener = new DiscordListener();
    private static final DiscordConsoleListener discordConsoleListener = new DiscordConsoleListener();
    static Pattern messageFilterRegex;

    @Override
    public void onEnable() {
        instance = this;
        loadConfig();
        LinkCommand.init();

        try {
            debug = config.getBoolean("debug");
            if (debug) {
                getLogger().info("Running DiscordChat in debug mode");
            }

            /** if (isLuckPermsEnabled()) {
                try {
                    luckPerms = LuckPermsProvider.get();
                    getLogger().info("LuckPerms detected and API is available!");
                } catch (Exception e) {
                    if (config.getBoolean("addRoleOnMinecraft")) {
                        getLogger().warning("LuckPerms is enabled, but API is not accessible.");
                        this.disable();
                        return;
                    }
                }
            } else {
                if (config.getBoolean("addRoleOnMinecraft")) {
                    getLogger().warning("LuckPerms is not installed or disabled!");
                    this.disable();
                    return;
                }
            } */

            String pattern = config.getString("messageFilterRegex", "");
            if (!pattern.isEmpty()) {
                messageFilterRegex = Pattern.compile(pattern);
            }

            jda = JDABuilder.createDefault(config.getString("botToken")).build();
            jda.awaitReady();

            Server.getInstance().getEventManager().registerListener(this, playerListener);
            jda.addEventListener(discordListener);

            channelId = config.getString("channelId", "null");
            if (config.getBoolean("discordConsole")) {
                consoleChannelId = config.getString("consoleChannelId", "null");
                jda.addEventListener(discordConsoleListener);
            }

            if (!config.getString("botStatus").isEmpty()) {
                jda.getPresence().setActivity(Activity.of(Activity.ActivityType.DEFAULT, config.getString("botStatus")));
            }

            if (queueMessages = config.getBoolean("queueMessages")) {
                Server.getInstance().getScheduler().scheduleRepeatingTask(this, messageQueue = new MessageQueue(), 20);
            }

            if (jda.getGuilds().isEmpty()) {
                getLogger().warning("Your Discord bot is not on any server.");
            }

            getLogger().info("DiscordChat enabled successfully.");
        } catch (Exception e) {
            getLogger().error("Error enabling DiscordChat", e);
        }
    }

    @Override
    public void onDisable() {
        if (config.getBoolean("stopMessages")) {
            API.sendMessage(config.getString("status_server_stopped"));
        }
        if (jda != null) {
            if (messageQueue != null) {
                messageQueue.run();
            }
            jda.shutdown();
        }
    }

    private void loadConfig() {
    config = new Config(getDataFolder() + "/config.yml", Config.YAML);

    if (!config.exists("configVersion")) {
        getLogger().warning("Config not found! Creating default config...");

        // Config version (do not edit)
        config.set("configVersion", 1);

        // Bot token
        config.set("botToken", "");
        // Discord server chat channel ID
        config.set("channelId", "");
        
        // Enable debug messages
        config.set("debug", false);

        // Bot status (set "Playing ..." status)
        config.set("botStatus", "Minecraft");

        // Set text channel topic (leave empty to disable)
        config.set("channelTopic", "Powered by DiscordChat for AllayMC");

        // Discord command settings
        config.set("playerListCommand", true);
        config.set("ipCommand", true);
        config.set("serverIp", "play.example.com");
        config.set("serverPort", "19132");
        config.set("discordCommand", true);
        config.set("discordCommandOutput", "Join our Discord server at §e<put your invite here>§f!");

        // Linking system
        config.set("linkCommand", true);
        config.set("addRoleOnDiscord", true);
        config.set("discordRoleID", "1324119853866553449");
        config.set("addRoleOnMinecraft", false);
        config.set("minecraftRole", "discord");
        config.set("changeUsername", false);

        // Message toggles
        config.set("joinMessages", true);
        config.set("quitMessages", true);
        config.set("deathMessages", true);
        config.set("startMessages", true);
        config.set("stopMessages", true);

        // Cross-server chat settings
        config.set("enableDiscordToMinecraft", true);
        config.set("enableMinecraftToDiscord", true);
        config.set("enableMessagesToConsole", false);

        // Command prefix
        config.set("commandPrefix", "!");

        // Discord console settings
        config.set("discordConsole", false);
        config.set("consoleChannelId", "");
        config.set("consoleRole", "");
        config.set("consoleStatusMessages", true);
        config.set("logConsoleCommands", true);

        // Chat formatting
        config.set("discordToMinecraftChatFormatting", "§f[§bDiscord §f| %role%§f] %discordname% » %message%");
        config.set("minecraftToDiscordChatFormatting", "%username% » %message%");

        // Message handling
        config.set("maxMessageLength", 255);
        config.set("allowBotMessages", false);
        config.set("queueMessages", true);

        // Anti-spam settings
        config.set("messageFilterRegex", "(?i)discord.*?\\..*?\\/|http.*?\\:.*?\\/\\/");
        config.set("messageFilterReplacement", "<link>");

        // Translations
        config.set("status_server_started", "**:white_check_mark: Server started!**");
        config.set("status_server_stopped", "**:x: Server stopped!**");
        config.set("info_player_joined", "**:heavy_plus_sign: %player% joined the server**");
        config.set("info_player_left", "**:heavy_minus_sign: %player% left the server**");
        config.set("info_player_death", "**:skull: %death_message%**");
        config.set("command_playerlist_empty", "**No online players**");
        config.set("command_playerlist_players", "Online players");
        config.set("commands_ip_address", "Address:");
        config.set("commands_ip_port", "Port:");
        config.set("err_no_perm", "You don't have permission to run console commands");
        config.set("console_status_server_start", "The server is starting up...");
        config.set("console_status_server_stop", "The server is shutting down...");
        config.set("command_mute_success", "§aDiscord chat muted");
        config.set("command_mute_already_muted", "§cDiscord chat is already muted");
        config.set("command_unmute_success", "§aDiscord chat is no longer muted");
        config.set("command_unmute_not_muted", "§cDiscord chat is not muted");
        config.set("command_generic_no_perm", "§cYou don't have permission to use this command");

        // Save default config
        config.save();
    }
   }

    /** private boolean isLuckPermsEnabled() {
        return Server.getInstance().getPluginManager().isPluginEnabled("LuckPerms");
    } */

    public static Loader getInstance() {
        return instance;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "discord":
                return DiscordCommand.handleCommand(sender, command, label, args);
            case "linkdiscord":
                return LinkCommand.handleCommand(sender, command, label, args);
            case "unlinkdiscord":
                return UnlinkCommand.handleCommand(sender, command, label, args);
            default:
                return false;
        }
    }
}
