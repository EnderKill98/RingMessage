package me.enderkill98.ringmessage.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

public class ChatUtil {

    public static void chat(MinecraftClient client, String messageOrCommand) {
        if(messageOrCommand.startsWith("/")) {
            sendCommand(client, messageOrCommand.substring(1));
        }else {
            if(messageOrCommand.startsWith("\\/"))
                messageOrCommand = messageOrCommand.substring(1);
            sendChatMessage(client, messageOrCommand);
        }
    }

    public static void sendChatMessage(MinecraftClient client, String message) {
        if(message.length() > 256) message = message.substring(0, 256);
        ClientPlayNetworkHandler network = client.getNetworkHandler();
        if(network == null) return;
        network.sendChatMessage(message);
    }

    public static void sendCommand(MinecraftClient client, String command) {
        if(command.length() > 256) command = command.substring(0, 256);
        ClientPlayNetworkHandler network = client.getNetworkHandler();
        if(network == null) return;
        String cmd = command.contains(" ") ? command.split(" ")[0] : command;
        if(cmd.equalsIgnoreCase("w") || cmd.equalsIgnoreCase("whisper")
                || cmd.equalsIgnoreCase("msg") || cmd.equalsIgnoreCase("tell")
                || cmd.equalsIgnoreCase("teammsg") || cmd.equalsIgnoreCase("me")) {
            network.sendChatCommand(command);
        }else {
            network.sendCommand(command);
        }
    }

}
