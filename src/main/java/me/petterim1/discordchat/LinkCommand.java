package me.petterim1.discordchat;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.utils.Config;

import java.io.File;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;
import java.util.Map;

public class LinkCommand {
    private static final HashMap<UUID, String> verificationCodes = new HashMap<>();
    private static Config config;
    private static Config verifiedLinks;

    // Initialize the config
    public static void init() {
        File file = new File(Server.getInstance().getDataPath(), "verification_codes.yml");
        config = new Config(file, Config.YAML);
        
        File verifiedFile = new File(Server.getInstance().getDataPath(), "verified_links.yml");
        verifiedLinks = new Config(verifiedFile, Config.YAML);
    
        loadVerificationCodes();
    }

    // Load verification codes from the config
    private static void loadVerificationCodes() {
        if (config == null) {
        Server.getInstance().getLogger().warning("[LinkCommand] Config is not initialized!");
        return;
    }
        verificationCodes.clear();
        for (String key : config.getKeys(false)) {
            verificationCodes.put(UUID.fromString(key), config.getString(key));
        }
    }

    // Save verification codes to the config
    private static void saveVerificationCodes() {
        if (config == null) {
        Server.getInstance().getLogger().warning("[LinkCommand] Config is not initialized!");
        return;
    }
        for (UUID uuid : verificationCodes.keySet()) {
            config.set(uuid.toString(), verificationCodes.get(uuid));
        }
        config.save();
    }

    public static boolean handleCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used in-game!");
            return false;
        }
        
        Player player = (Player) sender; 

        if (Loader.config.getBoolean("linkCommand") == true) {

        UUID uuid = player.getUniqueId();
        
        // Prevent generating a code if already linked
        if (isAlreadyLinked(uuid)) {
           player.sendMessage("§cYour Minecraft account is already linked to a Discord account!");
           return false;
        }

        if (verificationCodes.containsKey(uuid)) {
            player.sendMessage("§cYou already have a verification code: §b" + verificationCodes.get(uuid));
            player.sendMessage("§7Use this code in the Discord bot to verify your account.");
            return false;
        }

        // Generate a random 6-digit code
        String code = String.valueOf(100000 + new Random().nextInt(900000));
        verificationCodes.put(uuid, code);
        saveVerificationCodes();  // Save the code persistently

        player.sendMessage("§aYour verification code: §b" + code);
        player.sendMessage("§7Enter this code in the Discord bot to verify your account.");
        return true;
    } else {
    	player.sendMessage("§aThis command is disabled! Please contact a Server Administrator for further enquiries");
        return false;
     }
    } 

    public static String getCode(UUID uuid) {
        return verificationCodes.get(uuid);
    }

    public static boolean verifyCode(UUID uuid, String code) {
        return verificationCodes.containsKey(uuid) && verificationCodes.get(uuid).equals(code);
    }
    
    public static UUID getUUIDByCode(String code) {
    for (Map.Entry<UUID, String> entry : verificationCodes.entrySet()) {
        if (entry.getValue().equals(code)) {
            return entry.getKey();
        }
    }
    return null;
}

    public static void removeCode(UUID uuid) {
        if (config == null) {
        Server.getInstance().getLogger().warning("[LinkCommand] Config is not initialized!");
        return;
    }
        verificationCodes.remove(uuid);
        config.remove(uuid.toString());
        config.save();
    }
    
    public static boolean isAlreadyLinked(UUID uuid) {
    return verifiedLinks.exists(uuid.toString());
}

public static void addVerifiedAccount(UUID uuid, String discordId) {
    verifiedLinks.set(uuid.toString(), discordId);
    verifiedLinks.save();
}

public static UUID getUUIDByName(String playerName) {
    for (String key : verifiedLinks.getKeys(false)) {
        UUID uuid = UUID.fromString(key);
        String storedName = Server.getInstance().getOfflinePlayer(uuid).getName(); // Fetch stored name
        if (storedName != null && storedName.equalsIgnoreCase(playerName)) {
            return uuid;
        }
    }
    return null; // Return null if no match is found
}


public static void removeVerifiedAccount(UUID uuid) {
    verifiedLinks.remove(uuid.toString());
    verifiedLinks.save();
}
}
