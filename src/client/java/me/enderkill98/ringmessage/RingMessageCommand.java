package me.enderkill98.ringmessage;

import me.enderkill98.ringmessage.config.RingConfig;
import me.enderkill98.ringmessage.util.StringUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.HashMap;

public class RingMessageCommand {

    private void sendUsage(MinecraftClient client, String cmdName) {
        sendMessage(client, "§eUsage: $" + cmdName + " <members <show/set/setForAll ...>/send <message...>/debug [on/off]/alwaysEncrypt [on/off]/test <Member>/directUse [on/off]>");
    }

    private void sendMessage(MinecraftClient client, String message) {
        sendMessage(client, message, true);
    }

    private void sendMessage(MinecraftClient client, String message, boolean prefix) {
        if(client.player != null)
            client.player.sendMessage(Text.of((prefix ? ClientMod.PREFIX : "") + message));
    }

    public void onExecute(String cmdName, String[] args) {
        MinecraftClient client = MinecraftClient.getInstance();

        if(client.player == null) return;
        if(args.length == 0) {
            sendUsage(client, cmdName);
            return;
        }

        if(args[0].equalsIgnoreCase("members") && args.length > 1) {
            if(args[1].equalsIgnoreCase("show")) {
                sendMessage(client, "§aMembers: §2" + StringUtil.joinCommaSeparated(RingConfig.getInstance().ringMembers));
                return;
            }else if(args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("setForAll")) {
                if(args.length == 2 && !args[1].equalsIgnoreCase("setForAll")) {
                    RingConfig.getInstance().ringMembers.clear();
                    RingConfig.getInstance().save();
                    sendMessage(client, "§aCleared Members.");
                    return;
                }else {
                    String remainingArgs = StringUtil.join(" ", " ", Arrays.stream(args).skip(2));
                    remainingArgs = remainingArgs.replace(",", " ");
                    while(remainingArgs.contains("  "))
                        remainingArgs = remainingArgs.replace("  ", " ");

                    Ring.OrderedMemberRing ring = args[1].equalsIgnoreCase("setForAll") ? RingMessage.createNewRing(client) : null;

                    String[] members = remainingArgs.contains(" ") ? remainingArgs.split(" ") : new String[] { remainingArgs };
                    RingConfig.getInstance().ringMembers.clear();
                    RingConfig.getInstance().ringMembers.addAll(Arrays.asList(members));
                    RingConfig.getInstance().save();
                    sendMessage(client, "§aSet" + (ring == null ? "" : " and sent") + " Members: §2" + StringUtil.joinCommaSeparated(RingConfig.getInstance().ringMembers));

                    if(ring != null)
                        RingMessage.sendNewRingSyncMembersMessage(client, ring, RingConfig.getInstance().ringMembers);
                    return;
                }
            }
        }

        if(args[0].equalsIgnoreCase("send")) {
            String message = StringUtil.join(" ", " ", Arrays.stream(args).skip(1));
            Ring.OrderedMemberRing ring = RingMessage.createNewRing(client);
            if(ring == null) {
                sendMessage(client, "§cFailed to create new ring!");
                return;
            }
            if(!RingMessage.sendNewRingChatMessage(client, ring, message))
                sendMessage(client, "§cFailed to send off message. See logs for more!");
            return;
        }

        if(args[0].equalsIgnoreCase("test") && args.length == 2) {
            String targetMember = args[1];
            if(RingMessage.sendNewRingBasicTestMessage(client, targetMember)) {
                sendMessage(client, "§eTesting §6" + targetMember + "§e...");
            }else {
                sendMessage(client, "§cFailed to send off test. See logs for more!");
            }
            return;
        }

        if(args[0].equalsIgnoreCase("directUse")) {
            if(args.length == 1) {
                sendMessage(client, "§aDirectUse: " + (RingConfig.getInstance().directUse ? "§2On" : "§4Off"));
                sendUsage(client, cmdName);
                return;
            }

            if(args[1].equalsIgnoreCase("on")) {
                RingConfig.getInstance().directUse = true;
                RingConfig.getInstance().save();
                sendMessage(client, "§aDirectUse: " + (RingConfig.getInstance().directUse ? "§2On" : "§4Off"));
            }else if(args[1].equalsIgnoreCase("off")) {
                RingConfig.getInstance().directUse = false;
                RingConfig.getInstance().save();
                sendMessage(client, "§aDirectUse: " + (RingConfig.getInstance().directUse ? "§2On" : "§4Off"));
            }else {
                sendMessage(client, "§cPlease specify either \"On\" or \"Off\"!");
            }
            return;
        }

        if(args[0].equalsIgnoreCase("debug")) {
            if(args.length == 1) {
                sendMessage(client, "§aDebug: " + (RingConfig.getInstance().debug ? "§2On" : "§4Off"));
                sendUsage(client, cmdName);
                return;
            }

            if(args[1].equalsIgnoreCase("on")) {
                RingConfig.getInstance().debug = true;
                RingConfig.getInstance().save();
                sendMessage(client, "§aDebug: " + (RingConfig.getInstance().debug ? "§2On" : "§4Off"));
            }else if(args[1].equalsIgnoreCase("off")) {
                RingConfig.getInstance().debug = false;
                RingConfig.getInstance().save();
                sendMessage(client, "§aDebug: " + (RingConfig.getInstance().debug ? "§2On" : "§4Off"));
            }else {
                sendMessage(client, "§cPlease specify either \"On\" or \"Off\"!");
            }
            return;
        }

        if(args[0].equalsIgnoreCase("alwaysEncrypt")) {
            if(args.length == 1) {
                sendMessage(client, "§aAlways Encrypt (own Messages): " + (RingConfig.getInstance().alwaysEncrypt ? "§2On" : "§4Off"));
                sendUsage(client, cmdName);
                return;
            }

            if(args[1].equalsIgnoreCase("on")) {
                RingConfig.getInstance().alwaysEncrypt = true;
                RingConfig.getInstance().save();
                sendMessage(client, "§aAlways Encrypt (own Messages): " + (RingConfig.getInstance().alwaysEncrypt ? "§2On" : "§4Off"));
            }else if(args[1].equalsIgnoreCase("off")) {
                RingConfig.getInstance().alwaysEncrypt = false;
                RingConfig.getInstance().save();
                sendMessage(client, "§aAlways Encrypt (own Messages): " + (RingConfig.getInstance().alwaysEncrypt ? "§2On" : "§4Off"));
            }else {
                sendMessage(client, "§cPlease specify either \"On\" or \"Off\"!");
            }
            return;
        }

        sendUsage(client, cmdName);
    }

}
