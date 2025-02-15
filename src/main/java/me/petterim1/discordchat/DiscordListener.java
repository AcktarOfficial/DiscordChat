package me.petterim1.discordchat;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.utils.TextFormat;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.Node;
import net.luckperms.api.model.user.User;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class DiscordListener extends ListenerAdapter {

    static final Set<String> chatMuted = ConcurrentHashMap.newKeySet();
    static final List<DiscordChatReceiver> receivers = new ArrayList<>();
    private static final Pattern newline = Pattern.compile("\\r\\n|\\r|\\n");

    @Override
    public void onGuildMessageReceived(@NotNull GuildMessageReceivedEvent e) {
        if (e.getMember() == null || Loader.jda == null || e.getAuthor().equals(Loader.jda.getSelfUser())) {
            return;
        }

        // hacky way for the command cuz your discord command handler is too good for me xD
        
        String messagde = e.getMessage().getContentRaw().trim();
        if (messagde.startsWith("!verify ")) {
            String[] parts = messagde.split(" ", 2);
            if (parts.length < 2) {
                e.getChannel().sendMessage("❌ Usage: `!verify <code>`").queue();
                return;
            }

            String code = parts[1].trim();
            processVerification(e, code);
            return;
        }
        
        for (DiscordChatReceiver receiver : receivers) {
            receiver.receive(e);
        }
        if (!e.getChannel().getId().equals(Loader.channelId)) {
            return;
        }
        if (e.getAuthor().isBot() && !Loader.config.getBoolean("allowBotMessages")) {
            return;
        }
        String message = TextFormat.clean(e.getMessage().getContentStripped(), true);
        if (message.trim().isEmpty()) {
            return;
        }
        if (processDiscordCommand(message)) {
            return;
        }
        if (!Loader.config.getBoolean("enableDiscordToMinecraft")) {
            return;
        }
        if (message.length() > Loader.config.getInt("maxMessageLength")) {
            message = message.substring(0, Loader.config.getInt("maxMessageLength"));
        }
        message = newline.matcher(message).replaceAll(" ");
        if (message.trim().isEmpty()) {
            return;
        }
        String role = getColoredRole(getRole(e.getMember()));
        String name = TextFormat.clean(e.getMember().getEffectiveName(), true);
        String out = Loader.config.getString("discordToMinecraftChatFormatting")
                .replace("%role%", role)
                .replace("%discordname%", name)
                .replace("%message%", message);
        for (Player player : Server.getInstance().getOnlinePlayers().values()) {
            if (!chatMuted.contains(player.getName())) {
                player.sendMessage(out);
            }
        }
        if (Loader.config.getBoolean("enableMessagesToConsole")) {
            Server.getInstance().getLogger().info(out);
        }
    }

    private boolean processDiscordCommand(String m) {
        String prefix = Loader.config.getString("commandPrefix");
        if (Loader.config.getBoolean("playerListCommand") && m.equalsIgnoreCase(prefix + "playerlist")) {
            Map<UUID, Player> playerList = Server.getInstance().getOnlinePlayers();
            if (playerList.isEmpty()) {
                API.sendMessage(Loader.config.getString("command_playerlist_empty"));
            } else {
                String playerListMessage = "";
                playerListMessage += "**" + Loader.config.getString("command_playerlist_players") + " (" + playerList.size() + '/' + Server.getInstance().getMaxPlayers() + "):**";
                playerListMessage += "\n```\n";
                StringJoiner players = new StringJoiner(", ");
                List<String> sorted = new ArrayList<>(playerList.size());
                for (Player player : playerList.values()) {
                    sorted.add(player.getName());
                }
                sorted.sort(String.CASE_INSENSITIVE_ORDER);
                for (String playerName : sorted) {
                    players.add(playerName);
                }
                playerListMessage += players.toString();
                if (playerListMessage.length() > 1996) {
                    playerListMessage = playerListMessage.substring(0, 1993) + "...";
                }
                playerListMessage += "\n```";
                API.sendMessage(playerListMessage);
            }
            return true;
        } else if (Loader.config.getBoolean("ipCommand") && m.equalsIgnoreCase(prefix + "ip")) {
            API.sendMessage("```\n" + Loader.config.getString("commands_ip_address") + ' ' + Loader.config.getString("serverIp") + '\n' + Loader.config.getString("commands_ip_port") + ' ' + Loader.config.getString("serverPort") + "\n```");
            return true;
        }
        return false;
    }

    private void processVerification(GuildMessageReceivedEvent event, String code) {
        Player matchedPlayer = null;
        UUID uuid = null;
        String playerName = "Unknown";

        for (Player player : Server.getInstance().getOnlinePlayers().values()) {
            if (LinkCommand.verifyCode(player.getUniqueId(), code)) {
                uuid = player.getUniqueId();
                matchedPlayer = player;
                break;
            }
        }

        if (uuid == null) {
            uuid = LinkCommand.getUUIDByCode(code);
        }

        if (uuid != null) {
            playerName = matchedPlayer != null ? matchedPlayer.getName() : "Offline Player";

            if (LinkCommand.isAlreadyLinked(uuid)) {
                event.getChannel().sendMessage("❌ This Minecraft account is already linked to another Discord account!").queue();
                return;
            }

            if (Loader.config.getBoolean("addRoleOnMinecraft")) {
                boolean success = addPlayerToGroup(uuid, playerName);
            if (!success) {
                event.getChannel().sendMessage("An internal error occured! See below for more information. Please contact an administrator.").queue();
                return;
               }
            }

            if (Loader.config.getBoolean("addRoleOnDiscord")) {
                Role verifiedRole = event.getGuild().getRoleById(Loader.config.getString("discordRoleID"));
                if (verifiedRole != null) {
                    event.getGuild().addRoleToMember(event.getMember(), verifiedRole).queue();
                } else {
                    event.getChannel().sendMessage("Due to a Configuration issue, we were unable to complete your verification! Please contact an administrator");
                    return;
                }
            }

            if (Loader.config.getBoolean("changeUsername")) {
                event.getGuild().modifyNickname(event.getMember(), playerName).queue(
                        success -> event.getChannel().sendMessage("✅ Your Discord nickname has been updated!").queue(),
                        throwable -> event.getChannel().sendMessage("⚠️ Failed to update your Discord nickname.").queue()
                );
            }
            
            LinkCommand.removeCode(uuid);
            LinkCommand.addVerifiedAccount(uuid, event.getAuthor().getId());
        
        event.getChannel().sendMessage("✅ Your Minecraft account has been linked successfully!").queue();
        
        } else {
            event.getChannel().sendMessage("❌ Invalid verification code!").queue();
        }
    }

    private boolean addPlayerToGroup(UUID uuid, String playerName) {
    LuckPerms luckPerms = LuckPermsProvider.get();
    User user = luckPerms.getUserManager().getUser(uuid);
    
    if (user == null) {
        user = luckPerms.getUserManager().loadUser(uuid).join();
        if (user == null) {
            Loader.getInstance().getLogger().info("Failed to load user data for " + playerName);
            API.sendMessage("```Failed to load user data for " + playerName + "! Please contact an administrator regarding this issue.```");
            return false;
        }
    }

    String groupName = Loader.config.getString("minecraftRole");
    if (groupName == null || groupName.isEmpty()) {
        Loader.getInstance().getLogger().info("Config value 'minecraftRole' is missing or empty.");
        API.sendMessage("```Config value 'minecraftRole' is missing or empty! Please contact an administrator regarding this issue.```");
        return false;
    }
    
    Group group = luckPerms.getGroupManager().getGroup(groupName);
    if (group == null) {
        Loader.getInstance().getLogger().info("Group '" + groupName + "' does not exist!");
        API.sendMessage("```Group '" + groupName + "' does not exist! Please contact an administrator regarding this issue.```");
        return false;
    }
    
  // Check if the user already has the group
    if (user.getNodes().stream().anyMatch(node -> node.getKey().equals("group." + groupName))) {
        Loader.getInstance().getLogger().info(playerName + " is already in the group: " + groupName);
        API.sendMessage("```" + playerName + " is already in the group: " + groupName + "```");
        return true;
    }
    
    try {
        user.setPrimaryGroup(groupName);
        luckPerms.getUserManager().saveUser(user);
        Loader.getInstance().getLogger().info(playerName + " has been promoted to the " + groupName + " rank!");
        API.sendMessage(playerName + " has been promoted to the " + groupName + " rank! 🎁🥳🎉");

        // Notify the player if they are online
        Player onlinePlayer = Server.getInstance().getPlayerExact(playerName);
        if (onlinePlayer != null) {
            onlinePlayer.sendMessage("You have been rewarded with the: " + groupName + " rank! Thanks for linking your Discord Account!");
        }
        return true;
    } catch (Exception ex) {
        Loader.getInstance().getLogger().warning("Failed to set primary group for " + playerName + ": " + ex.getMessage());
        API.sendMessage("```A critical error occured while setting your new rank! Please contact an administrator regarding this issue.```");
        return false;
    }
    }

    private static Role getRole(Member m) {
        for (Role role : m.getRoles()) {
            return role;
        }
        return null;
    }

    private static String getColoredRole(Role r) {
        if (r == null) {
            return "";
        }
        Color color = r.getColor();
        if (color == null) {
            return TextFormat.WHITE + r.getName();
        } else {
            return fromRGB(color.getRed(), color.getGreen(), color.getBlue()) + r.getName();
        }
    }

    // Source: https://minecraft.fandom.com/wiki/Formatting_codes
    private final static Map<TextFormat, ColorSet> COLORS = new HashMap<>();

    static {
        COLORS.put(TextFormat.BLACK, new ColorSet(0, 0, 0));
        COLORS.put(TextFormat.DARK_BLUE, new ColorSet(0, 0, 170));
        COLORS.put(TextFormat.DARK_GREEN, new ColorSet(0, 170, 0));
        COLORS.put(TextFormat.DARK_AQUA, new ColorSet(0, 170, 170));
        COLORS.put(TextFormat.DARK_RED, new ColorSet(170, 0, 0));
        COLORS.put(TextFormat.DARK_PURPLE, new ColorSet(170, 0, 170));
        COLORS.put(TextFormat.GOLD, new ColorSet(255, 170, 0));
        COLORS.put(TextFormat.GRAY, new ColorSet(170, 170, 170));
        COLORS.put(TextFormat.DARK_GRAY, new ColorSet(85, 85, 85));
        COLORS.put(TextFormat.BLUE, new ColorSet(85, 85, 255));
        COLORS.put(TextFormat.GREEN, new ColorSet(85, 255, 85));
        COLORS.put(TextFormat.AQUA, new ColorSet(85, 255, 255));
        COLORS.put(TextFormat.RED, new ColorSet(255, 85, 85));
        COLORS.put(TextFormat.LIGHT_PURPLE, new ColorSet(255, 85, 255));
        COLORS.put(TextFormat.YELLOW, new ColorSet(255, 255, 85));
        COLORS.put(TextFormat.WHITE, new ColorSet(255, 255, 255));
        COLORS.put(TextFormat.MINECOIN_GOLD, new ColorSet(221, 214, 5));
    }

    private static class ColorSet {

        final int red;
        final int green;
        final int blue;

        ColorSet(int red, int green, int blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
        }
    }

    // Source: https://gist.github.com/mikroskeem/428f82fbf12f52f29cc6199482c77fb5
    private static TextFormat fromRGB(int r, int g, int b) {
        TreeMap<Integer, TextFormat> closest = new TreeMap<>();
        COLORS.forEach((color, set) -> {
            int red = Math.abs(r - set.red);
            int green = Math.abs(g - set.green);
            int blue = Math.abs(b - set.blue);
            closest.put(red + green + blue, color);
        });
        return closest.firstEntry().getValue();
    }
}
