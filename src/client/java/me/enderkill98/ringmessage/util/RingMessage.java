package me.enderkill98.ringmessage.util;

import me.enderkill98.ringmessage.ClientMod;
import me.enderkill98.ringmessage.config.RingConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public class RingMessage {

    private static Random RNG = new Random();
    private static String letterDict = "abcdefghijklmnopqrstuvwxyz" + "abcdefghijklmnopqrstuvwxyz".toUpperCase() + "0123456789";


    public static boolean handleReceivedMessage(String senderUserName, Text message) {
        return handleReceivedMessage(senderUserName, message.getString());
    }

    public static boolean handleReceivedMessage(@Nullable String receivedFromUserName, String messageStr) {
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
        if(receivedFromUserName != null && !RingConfig.getInstance().ringMembers.contains(receivedFromUserName)) {
            System.err.println("[RingMessage] Failed to handle Ring message: Sender not in Members!");
            return false; // Unauthorized sender
        }

        if(!messageStr.contains("]")) {
            System.err.println("[RingMessage] Failed to handle Ring message: No Header end found!");
            return false; // No header end
        }
        int headerCloseCharIndex = messageStr.indexOf(']');
        String headerPart = messageStr.substring(1, headerCloseCharIndex); // rm...
        String messagePart = messageStr.substring(headerCloseCharIndex+1); // Everything after the "]"

        HashMap<String, String> headerFields = new HashMap<>();
        if(headerPart.startsWith("rm:")) {
            String strippedHeaderPart = headerPart.substring("rm:".length());
            for(String keyAndMaybeValue : (strippedHeaderPart.contains(":") ? strippedHeaderPart.split(":") : new String[] { strippedHeaderPart })) {
                if(keyAndMaybeValue.contains("=")) {
                    int indexOfEquals = keyAndMaybeValue.indexOf('=');
                    headerFields.put(keyAndMaybeValue.substring(0, indexOfEquals), keyAndMaybeValue.substring(indexOfEquals+1));
                }else {
                    headerFields.put(keyAndMaybeValue, ""); // No value
                }
            }
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
        HashSet<String> onlineMembers = getOnlineMembers(client, RingConfig.getInstance().ringMembers);
        @Nullable String nextMember = findNextMember(senderUserName, client.getSession().getUsername(), onlineMembers, hashModification);
        if(nextMember != null) {
            String fullMessage = " [" + headerPart + "]" + messagePart;
            if(NoChatReportsUtil.isModAvailable() && NoChatReportsUtil.isEnabled())
                fullMessage = NoChatReportsUtil.encrypt(fullMessage);
            ChatUtil.sendCommand(client, "msg " + nextMember + " " + fullMessage);
        }
        return true;
    }

    public static HashSet<String> getOnlineMembers(MinecraftClient client, HashSet<String> members) {
        HashSet<String> onlineMembers = new HashSet<>();
        if(client.getNetworkHandler() == null)
            return onlineMembers;
        for(String member : members) {
            if(client.getNetworkHandler().getPlayerList().stream().anyMatch((entry) -> entry.getProfile().getName().equalsIgnoreCase(member)))
                onlineMembers.add(member);
        }
        return onlineMembers;
    }

    public static int hash(String userName, @Nullable String hashModification) {
        userName = userName.toLowerCase();
        String hashStr = userName + (hashModification != null ? ":" + hashModification : "");
        // Create MD5 Hash
        try {
            MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(hashStr.getBytes(StandardCharsets.UTF_8));
            return (hash[4] << 3*8) | (hash[5] << 2*8) | (hash[6] << 8) | (hash[7]);
        }catch (Exception ex) {
            throw new RuntimeException("WTF, this should never happen! You don't have MD5!");
        }
    }

    public static @Nullable String findNextMember(String senderUserName, String afterUserName, HashSet<String> members, @Nullable String hashModification) {
        int selfHash = hash(afterUserName,  hashModification);

        HashMap<Integer, String> hashToMember = new HashMap<>();
        ArrayList<Integer> orderedHashes = new ArrayList<>();
        for(String member : members) {
            int hash = hash(member, hashModification);
            if(hashToMember.containsKey(hash)) {
                // Hash collision, can't determine next
                return null;
            }
            hashToMember.put(hash, member);
            orderedHashes.add(hash);
        }
        if(!hashToMember.containsKey(selfHash)) {
            hashToMember.put(selfHash, afterUserName);
            orderedHashes.add(selfHash);
        }

        Collections.sort(orderedHashes);
        int nextHash = orderedHashes.get((orderedHashes.indexOf(selfHash) + 1) % orderedHashes.size());
        if(nextHash == hash(senderUserName, hashModification))
            return null; // Ring completed
        return hashToMember.get(nextHash);
    }

    public static void sendNewRingMessage(MinecraftClient client, String message) {
        HashSet<String> onlineMembers = RingMessage.getOnlineMembers(client, RingConfig.getInstance().ringMembers);
        System.out.println("Online Members: " + StringUtil.joinCommaSeparated(onlineMembers));
        String hashModification = "";
        for(int i = 0; i < 6; i++)
            hashModification += letterDict.charAt(RNG.nextInt(letterDict.length()));

        @Nullable String nextMember = RingMessage.findNextMember(client.getSession().getUsername(), client.getSession().getUsername(), onlineMembers, hashModification);
        if(nextMember != null) {
            String fullMessage = " [rm:s=" + client.getSession().getUsername() + ":h=" + hashModification + "]" + message;
            if(NoChatReportsUtil.isModAvailable() && NoChatReportsUtil.isEnabled())
                fullMessage = NoChatReportsUtil.encrypt(fullMessage);
            ChatUtil.sendCommand(client, "msg " + nextMember + " " + fullMessage);
            client.player.sendMessage(Text.of(ClientMod.PREFIX + "§2" + client.getSession().getUsername() + " » §a" + message));
        }else {
            client.player.sendMessage(Text.of(ClientMod.PREFIX + "§cNo next member found!"));
        }
    }
}
