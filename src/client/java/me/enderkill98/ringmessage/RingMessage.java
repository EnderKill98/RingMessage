package me.enderkill98.ringmessage;

import me.enderkill98.ringmessage.config.RingConfig;
import me.enderkill98.ringmessage.util.ChatUtil;
import me.enderkill98.ringmessage.util.NoChatReportsUtil;
import me.enderkill98.ringmessage.util.StringUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RingMessage {

    private static Random RNG = new Random();
    private static String letterDict = "abcdefghijklmnopqrstuvwxyz" + "abcdefghijklmnopqrstuvwxyz".toUpperCase() + "0123456789";

    public static boolean handleReceivedMessage(@Nullable String receivedFromUserName, String messageStr,
                                                @Nullable NoChatReportsUtil.DetailedDecryptionInfo ncrDecryptionInfo) {
        if(RingConfig.getInstance().ringMembers.isEmpty()) {
            System.err.println("[RingMessage] Failed to handle Ring message: No members configured!");
            return false;
        }

        String[] messageStrWords = messageStr.split(" ");
        boolean foundHeader = false;
        for(int i = 0; i < messageStrWords.length; i++) {
            if(messageStrWords[i].startsWith("[rm")) {
                messageStr = StringUtil.join(" ", " ", Arrays.stream(messageStrWords).skip(i));
                foundHeader = true;
                break;
            }
        }
        if(!foundHeader) {
            System.err.println("[RingMessage] Failed to handle Ring message: No Header start found!");
            return false;
        }
        if(receivedFromUserName != null && !Ring.contains(RingConfig.getInstance().ringMembers, receivedFromUserName)) {
            System.err.println("[RingMessage] Failed to handle Ring message: Sender is not in Members!");
            return false; // Unauthorized sender
        }

        if(!messageStr.contains("]")) {
            System.err.println("[RingMessage] Failed to handle Ring message: No Header end found!");
            return false; // No header end
        }
        int headerCloseCharIndex = messageStr.indexOf(']');
        String headerPart = messageStr.substring(0, headerCloseCharIndex+1); // rm...
        String messagePart = ""; // Empty = no message
        try {
            messagePart = messageStr.substring(headerCloseCharIndex + 1); // Everything after the "]"
        }catch (IndexOutOfBoundsException ex) { } // No message

        HashMap<String, String> headerFields = decodeHeader(headerPart);
        if(headerFields == null) {
            System.err.println("[RingMessage] Failed to handle Ring message: Couldn't decode header!");
            return false;
        }

        @Nullable String hashModification = null;
        if(headerFields.containsKey("h")) // Hash Modification
            hashModification = headerFields.get("h");
        String senderUserName;
        if(headerFields.containsKey("s")) // Hash Modification
            senderUserName = headerFields.get("s");
        else {
            System.err.println("[RingMessage] Failed to handle Ring message: Missing required field \"s\"!");
            return false; // Field is required!
        }

        if(!RingConfig.getInstance().ringMembers.contains(senderUserName)) {
            System.err.println("[RingMessage] Failed to handle Ring message: Original sender not in members!");
            return false;
        }

        // Todo verify own hash was correct (so sender was rightfully sending to self)

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if(player != null)
            player.sendMessage(Text.of(ClientMod.PREFIX + "§2" + (senderUserName != null ? senderUserName : "§onull") + "§2 » §a" + messagePart));
        MinecraftClient client = MinecraftClient.getInstance();
        HashSet<String> onlineMembers = Ring.getOnlineMembers(client, RingConfig.getInstance().ringMembers);
        Ring.OrderedMemberRing ring = Ring.OrderedMemberRing.ofOnlineMembers(onlineMembers, client.getSession().getUsername(), hashModification);
        if(ring == null) {
            System.err.println("[RingMessage] Failed to handle Ring message: Couldn't recreate ring!");
            return false;
        }

        String nextMember = ring.after(client.getSession().getUsername());
        if(!senderUserName.equalsIgnoreCase(client.getSession().getUsername())) {
            String fullMessage = " [" + headerPart + "]" + messagePart;
            if(NoChatReportsUtil.isModAvailable()) {
                // (Re-)encrypt if suitable

                if(!NoChatReportsUtil.supportsDecryptDetailed() && NoChatReportsUtil.isEnabled()) {
                    // Does not support finding out the details of how a message got delivered,
                    // since using official mod. Encrypting with own defaults if mod is active.
                    fullMessage = NoChatReportsUtil.encrypt(fullMessage);
                }
                if(NoChatReportsUtil.supportsDecryptDetailed() && ncrDecryptionInfo != null) {
                    // Received message was encrypted with given details. Use same params for sending along
                    // Prefer to compress if sender also compressed but use own defaults (all compressions should work rn on the mod)
                    fullMessage = NoChatReportsUtil.encryptAndCompress(ncrDecryptionInfo.keyIndex(), ncrDecryptionInfo.encapsulation(),
                            "msg " + nextMember + " ", fullMessage,
                            ncrDecryptionInfo.compression() != null ? NoChatReportsUtil.CompressionPolicy.Preferred : NoChatReportsUtil.CompressionPolicy.Never,
                            null /* = Auto Determine */);
                }
            }
            if(!RingConfig.getInstance().debug)
                // Hide sent message confirmation
                ClientMod.INSTANCE.hideSentFullMessages.put(fullMessage.trim(), System.currentTimeMillis() + 5000);
            if(fullMessage.startsWith("msg "))
                ChatUtil.sendCommand(client, fullMessage);
            else
                ChatUtil.sendCommand(client, "msg " + nextMember + " " + fullMessage);
        }else {
            // Got confirmation from last user in ring!
        }
        return true;
    }

    public static String escapeHeaderValue(String headerValue) {
        // Url encoding for the poor
        headerValue = headerValue.replace("=", "%3D");
        headerValue = headerValue.replace(":", "%3A");
        headerValue = headerValue.replace("]", "%5D");
        return headerValue;
    }

    public static String unescapeHeaderValue(String headerValue) {
        // Url decoding for the poor
        headerValue = headerValue.replace("%3D", "=");
        headerValue = headerValue.replace("%3A", ":");
        headerValue = headerValue.replace("%5D", "]");
        return headerValue;
    }

    public static String encodeHeader(HashMap<String, String> headerFields, boolean includeBrackets) {
        StringBuilder builder = new StringBuilder(includeBrackets ? "[rm" : "rm");
        for(Map.Entry<String, String> entry : headerFields.entrySet()) {
            builder.append(':');
            builder.append(entry.getKey());
            builder.append('=');
            builder.append(escapeHeaderValue(entry.getValue()));
        }
        if(includeBrackets)
            builder.append(']');
        return builder.toString();
    }

    public static @Nullable HashMap<String, String> decodeHeader(String rawHeader) {
        if(rawHeader.startsWith("["))
            rawHeader = rawHeader.substring(1);
        if(rawHeader.endsWith("]"))
            rawHeader = rawHeader.substring(0, rawHeader.length() - 1);


        HashMap<String, String> headerFields = new HashMap<>();
        if(!rawHeader.startsWith("rm")) return null; // Header MAGIC missing

        if(rawHeader.startsWith("rm:")) {
            rawHeader = rawHeader.substring("rm:".length());
            for(String keyAndMaybeValue : (rawHeader.contains(":") ? rawHeader.split(":") : new String[] { rawHeader })) {
                if(keyAndMaybeValue.contains("=")) {
                    int indexOfEquals = keyAndMaybeValue.indexOf('=');
                    headerFields.put(keyAndMaybeValue.substring(0, indexOfEquals), unescapeHeaderValue(keyAndMaybeValue.substring(indexOfEquals+1)));
                }else {
                    headerFields.put(keyAndMaybeValue, ""); // No value
                }
            }
        }
        return headerFields;
    }

    public static boolean sendNewRingMessage(MinecraftClient client, String message) {
        HashSet<String> onlineMembers = Ring.getOnlineMembers(client, RingConfig.getInstance().ringMembers);
        System.out.println("Online Members: " + StringUtil.joinCommaSeparated(onlineMembers));
        Ring.OrderedMemberRing ring = null;
        int attempts = 0;

        while(ring == null) {
            attempts++;
            String hashModification = "";
            for (int i = 0; i < 6; i++)
                hashModification += letterDict.charAt(RNG.nextInt(letterDict.length()));
            ring = Ring.OrderedMemberRing.ofOnlineMembers(onlineMembers, client.getSession().getUsername(), hashModification);
            if(ring == null)
                System.err.println("[RingMessage] Failed to create ring! Likely hash collision! (Attempt " + attempts + ")");
            if(attempts >= 10) {
                System.err.println("[RingMessage] Ran out of attempts, creating a ring!");
                return false;
            }
        }

        String nextMember = ring.after(client.getSession().getUsername());

        HashMap<String, String> headerFields = new HashMap<>();
        headerFields.put("s", client.getSession().getUsername());
        if(ring.getUsedHashModification() != null)
            headerFields.put("h", ring.getUsedHashModification());

        String fullMessage = encodeHeader(headerFields, true) + message;
        if(NoChatReportsUtil.isModAvailable() && (NoChatReportsUtil.isEnabled() || RingConfig.getInstance().alwaysEncrypt))
            fullMessage = NoChatReportsUtil.encrypt(fullMessage);
        if(!RingConfig.getInstance().debug)
            // Hide sent message confirmation
            ClientMod.INSTANCE.hideSentFullMessages.put(fullMessage.trim(), System.currentTimeMillis() + 5000);
        ChatUtil.sendCommand(client, "msg " + nextMember + " " + fullMessage);
        client.player.sendMessage(Text.of(ClientMod.PREFIX + "§2" + client.getSession().getUsername() + " » §a" + message));
        return true;
    }
}
