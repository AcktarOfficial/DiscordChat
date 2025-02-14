package me.petterim1.discordchat;

import java.util.UUID;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;

public class UnlinkCommand {

 static boolean handleCommand(CommandSender sender, Command command, String label, String[] args) {
      Player player = Server.getInstance().getPlayer(args[0]);
     
      if (Loader.config.getBoolean("linkCommand") == true) {

        if (args.length != 1) {
            sender.sendMessage("§cUsage: /unlinkdiscord <player>");
            return false;
        }
        
        // Get player UUID from name
        UUID uuid = (player != null) ? (UUID) player.getUniqueId() : LinkCommand.getUUIDByName(args[0]);

        if (uuid == null) {
            sender.sendMessage("§cPlayer not found or not linked!");
            return false;
        }
        
        // Check if player is actually linked
        if (!LinkCommand.isAlreadyLinked(uuid)) {
            sender.sendMessage("§cThis player is not linked to any Discord account!");
            return false;
        }

        // Remove the link
        LinkCommand.removeVerifiedAccount(uuid);

        sender.sendMessage("§aSuccessfully unlinked " + args[0] + " from Discord!");

        if (player != null) {
            player.sendMessage("§cYour Discord link has been removed by an admin.");
        }
      } else {
      	player.sendMessage("§cThe command is disabled! Please contact a server administrator for further enquiries");
      }
        return true;
    }
}

