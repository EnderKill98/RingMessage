package me.enderkill98.ringmessage;

import me.enderkill98.ringmessage.config.RingConfig;
import me.enderkill98.ringmessage.util.ChatUtil;
import me.enderkill98.ringmessage.util.NoChatReportsUtil;
import me.enderkill98.ringmessage.util.StringUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class RingMessage {

    public static final int PROTOCOL_VERSION = 2;

    private static Random RNG = new Random();
    private static String letterDict = "abcdefghijklmnopqrstuvwxyz" + "abcdefghijklmnopqrstuvwxyz".toUpperCase() + "0123456789";

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

        boolean wasEncrypted = false;
        NoChatReportsUtil.DetailedDecryptionInfo ncrDecryptionInfo = null;
        if(NoChatReportsUtil.isModAvailable() && !messagePart.isBlank()) {
            if(NoChatReportsUtil.supportsDecryptDetailed()) {
                Optional<NoChatReportsUtil.DetailedDecryptionInfo> decryptionInfo = NoChatReportsUtil.tryDecryptDetailed(messagePart);
                if(decryptionInfo.isPresent()) {
                    messagePart = decryptionInfo.get().decryptedText();
                    ncrDecryptionInfo = decryptionInfo.get();
                    wasEncrypted = true;
                }
            }else {
                // Most likely using official mod, which doesn't have the custom decryptDetailed() method
                String maybeDecrypted = NoChatReportsUtil.decrypt(messagePart);
                if(maybeDecrypted != null) {
                    messagePart = maybeDecrypted;
                    wasEncrypted = true;
                }
            }
        }

        HashMap<String, String> headerFields = decodeHeader(headerPart);
        if(headerFields == null) {
            System.err.println("[RingMessage] Failed to handle Ring message: Couldn't decode header!");
            return false;
        }

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

        @Nullable String hashModification = null;
        if(headerFields.containsKey("h")) // Hash Modification
            hashModification = headerFields.get("h");

        MinecraftClient client = MinecraftClient.getInstance();
        HashSet<String> onlineMembers = Ring.getOnlineMembers(client, RingConfig.getInstance().ringMembers);
        Ring.OrderedMemberRing ring = Ring.OrderedMemberRing.ofOnlineMembers(onlineMembers, client.getSession().getUsername(), hashModification);
        if(ring == null) {
            System.err.println("[RingMessage] Failed to handle Ring message: Couldn't recreate ring!");
            return false;
        }

        if(headerFields.containsKey("syncmembers")) {
            return handleReceivedMessageSyncMembers(client, ncrDecryptionInfo, wasEncrypted, headerFields, ring, messagePart);
        } else if(headerFields.containsKey("basictest")) {
                return handleReceivedBasicTestMessage(client, ncrDecryptionInfo, wasEncrypted, headerFields, ring, messagePart);
        } else if(!messagePart.isEmpty()) {
            return handleReceivedMessageChat(client, ncrDecryptionInfo, wasEncrypted, headerFields, ring, messagePart);
        }
        return true;
    }

    public static boolean handleReceivedMessageSyncMembers(MinecraftClient client,
                                                           NoChatReportsUtil.DetailedDecryptionInfo ncrDecryptionInfo,
                                                           boolean wasEncrypted, HashMap<String, String> headerFields,
                                                           Ring.OrderedMemberRing ring, String message) {
        String senderUserName = headerFields.get("s"); // Was checked to exist earlier
        String newRawMemberList = headerFields.get("syncmembers");
        if(newRawMemberList.isBlank()) {
            System.err.println("[RingMessage] Someone attempted (" + senderUserName + "?) to clear out all members!");
            return false;
        }
        System.out.println("[RingMessage] Got Sync Members Message from presumably " + senderUserName + ": " + newRawMemberList);

        // Pass along based on old ring
        if(shouldHandle(client, ring, ncrDecryptionInfo, headerFields, message, true)) {
            String[] newMembers = newRawMemberList.contains(",") ? newRawMemberList.split(",") : new String[] { newRawMemberList };
            RingConfig.getInstance().ringMembers.clear();
            Arrays.stream(newMembers).forEach(RingConfig.getInstance().ringMembers::add);
            RingConfig.getInstance().save();

            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if(player != null)
                player.sendMessage(Text.of(ClientMod.PREFIX + "§2" + senderUserName + "§e updated the Members to: §6" + StringUtil.joinCommaSeparated(RingConfig.getInstance().ringMembers)), false);
        }
        return true;
    }

    public static boolean handleReceivedBasicTestMessage(MinecraftClient client,
                                                         NoChatReportsUtil.DetailedDecryptionInfo ncrDecryptionInfo,
                                                         boolean wasEncrypted, HashMap<String, String> headerFields,
                                                         Ring.OrderedMemberRing ring, String message) {
        String senderUserName = headerFields.get("s"); // Was checked to exist earlier
        System.out.println("[RingMessage] Got Chat Message from presumably " + senderUserName + ": " + message);

        HashSet<String> twoMembers = new HashSet<>();
        twoMembers.add(client.getSession().getUsername());
        twoMembers.add(senderUserName);
        Ring.OrderedMemberRing twoMemberRing = Ring.OrderedMemberRing.ofOnlineMembers(twoMembers, null, ring.getUsedHashModification());

        int messageHash = hashOfMessage(headerFields, message);
        if(ClientMod.INSTANCE.expectConfirmationUntil.containsKey(messageHash)) {
            if(client.player != null) {
                String memberInfo;
                String protocolInfo;
                String modInfo;
                if(headerFields.containsKey("pv")) { // Protocol Version
                    if(!headerFields.get("pv").equals(PROTOCOL_VERSION + "")) {
                        protocolInfo = "§6Protocol version differs (You: §e" + PROTOCOL_VERSION + "§6, Them: §e" + headerFields.get("pv") + "§6)";
                    }else {
                        protocolInfo = "§aSame protocol version.";
                    }
                } else
                    protocolInfo = "§6No protocol version received.";
                if(headerFields.containsKey("mv")) { // Mod Version
                    if(!headerFields.get("mv").equals(ClientMod.MOD_VERSION)) {
                        modInfo = "§6Mod version differs (You: §e" + ClientMod.MOD_VERSION + "§6, Them: §e" + headerFields.get("mv") + "§6)";
                    }else {
                        modInfo = "§aSame mod version.";
                    }
                } else
                    modInfo = "§6No mod version received.";

                if(headerFields.containsKey("memberlist")) {
                    HashSet<String> otherMemberList = new HashSet<>(Arrays.asList(headerFields.get("memberlist").split(",")));

                    ArrayList<String> otherIsMissingMembers = new ArrayList<>();
                    ArrayList<String> otherHasExtraMembers = new ArrayList<>();

                    for(String ownMember : RingConfig.getInstance().ringMembers) {
                        if(!Ring.contains(otherMemberList, ownMember))
                            otherIsMissingMembers.add(ownMember);
                    }
                    for(String otherMember : otherMemberList) {
                        if(!Ring.contains(RingConfig.getInstance().ringMembers, otherMember))
                            otherHasExtraMembers.add(otherMember);
                    }

                    if(!otherIsMissingMembers.isEmpty() && !otherHasExtraMembers.isEmpty())
                        memberInfo = "§6Their Member List has §e" + StringUtil.join("§6, §e", "§6 and §e", otherHasExtraMembers) + "§6, which you don't have and are missing §e" + StringUtil.join("§6, §e", "§6 and §e", otherIsMissingMembers) + "§6!";
                    else if(otherIsMissingMembers.isEmpty() && !otherHasExtraMembers.isEmpty())
                        memberInfo = "§6Their Member List has §e" + StringUtil.join("§6, §e", "§6 and §e", otherHasExtraMembers) + "§6, which you don't have!";
                    else if(!otherIsMissingMembers.isEmpty() && otherHasExtraMembers.isEmpty())
                        memberInfo = "§6Their Member List is missing §e" + StringUtil.join("§6, §e", "§6 and §e", otherIsMissingMembers) + "§6!";
                    else
                        memberInfo = "§aTheir Member List matches yours!";
                }else {
                    memberInfo = "§6No Member List provided.";
                }
                client.player.sendMessage(Text.of(ClientMod.PREFIX + "§aTest successful! " + modInfo + " " + protocolInfo + " " + memberInfo), false);
            }
        }

        final HashMap<String, String> fHeaderFields = headerFields;
        MessageModifyHandler modifier = () -> {
            if(fHeaderFields.containsKey("id")) {
                HashMap<String, String> newHeaderFields = new HashMap<>(fHeaderFields);
                String memberList = StringUtil.join(",", ",", RingConfig.getInstance().ringMembers);
                if(memberList.length() < 200) { // Prevent message becoming too long
                    newHeaderFields.put("memberlist", StringUtil.join(",", ",", RingConfig.getInstance().ringMembers));
                }
                newHeaderFields.put("pv", PROTOCOL_VERSION + ""); // Protocol Version
                newHeaderFields.put("mv", ClientMod.MOD_VERSION); // Mod Version
                return new Pair<>(newHeaderFields, message);
            }else {
                // Don't change if no id field for backwards compatibility
                return new Pair<>(fHeaderFields, message);
            }
        };
        if(shouldHandle(client, twoMemberRing, ncrDecryptionInfo, headerFields, message, true, modifier))
            System.out.println("[RingMessage] " + senderUserName + " (presumably) probed you with a basic test.");
        return true;
    }

    public static boolean handleReceivedMessageChat(MinecraftClient client,
                                                    NoChatReportsUtil.DetailedDecryptionInfo ncrDecryptionInfo,
                                                    boolean wasEncrypted, HashMap<String, String> headerFields,
                                                    Ring.OrderedMemberRing ring, String message) {
        String senderUserName = headerFields.get("s"); // Was checked to exist earlier
        System.out.println("[RingMessage] Got Chat Message from presumably " + senderUserName + ": " + message + (ncrDecryptionInfo != null ? " (encrypted)" : ""));

        if(shouldHandle(client, ring, ncrDecryptionInfo, headerFields, message, true)) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if(player != null) {
                String encryptionTooltip = null;
                if(wasEncrypted && NoChatReportsUtil.isModAvailable()) {
                    if(ncrDecryptionInfo != null) {
                        encryptionTooltip = "§a🔒 §a§nEncrypted with No Chat Reports§a 🔒\n\n" +
                                "§a🔑 §2Key Index: §a" + ncrDecryptionInfo.keyIndex() + "\n" +
                                "§a🎁 §2Encapsulation: §a" + ncrDecryptionInfo.encapsulation();
                        if(ncrDecryptionInfo.compression() != null) {
                            encryptionTooltip += "\n§a🗜 §2Compression: §a" + ncrDecryptionInfo.compression().getCompressionName();
                            if(ncrDecryptionInfo.compressionRatio() == null)
                                encryptionTooltip += "§2 (No Ratio!?!)";
                            else
                                encryptionTooltip += "§2 (Ratio: §a" + BigDecimal.valueOf(ncrDecryptionInfo.compressionRatio()).setScale(2, RoundingMode.HALF_UP) + "§a)";
                        }
                    }else {
                        encryptionTooltip = "§a🔒 §a§nEncrypted with No Chat Reports§a 🔒\n\n§7Can't determine encryption details.\n§7Can only show details with this fork:\n§7github.com/EnderKill98/No-Chat-Reports";
                    }
                }

                showChatMessage(player, senderUserName, encryptionTooltip, message);
            }
        }
        return true;
    }

    public static void showChatMessage(@NotNull ClientPlayerEntity player, String senderUserName, @Nullable String encryptionTooltip, String message) {
        MutableText text = Text.of(ClientMod.PREFIX + "§2" + senderUserName).copy();
        if(encryptionTooltip != null)
            text.append(Text.literal(" 🔒").setStyle(Style.EMPTY.withColor(Formatting.GREEN).withHoverEvent(new HoverEvent.ShowText(Text.of(encryptionTooltip)))));
        text.append(Text.literal(" »").setStyle(Style.EMPTY.withColor(Formatting.GREEN)));
        text.append(Text.literal(" " + message).setStyle(Style.EMPTY.withColor(Formatting.GREEN)));
        player.sendMessage(text, false);
    }

    public static interface MessageModifyHandler {
        public Pair<HashMap<String, String>, String> getModifiedMessageToPassAlong();
    }

    public static boolean shouldHandle(MinecraftClient client, Ring.OrderedMemberRing ring,
                                       NoChatReportsUtil.DetailedDecryptionInfo ncrDecryptionInfo,
                                       HashMap<String, String> headerFields, String message,
                                       boolean passAlong) {
        return shouldHandle(client, ring, ncrDecryptionInfo, headerFields, message, passAlong, null);
    }

    public static boolean shouldHandle(MinecraftClient client, Ring.OrderedMemberRing ring,
                                       NoChatReportsUtil.DetailedDecryptionInfo ncrDecryptionInfo,
                                       HashMap<String, String> headerFields, String message,
                                       boolean passAlong, @Nullable MessageModifyHandler passAlongModifier) {
        String senderUserName = headerFields.get("s"); // Was checked to exist earlier

        String nextMember = ring.after(client.getSession().getUsername());
        if(nextMember == null) {
            System.err.println("[RingMessage] Unexcpected! Couldn't pass a message to next member!");
            return false;
        }

        if(!senderUserName.equalsIgnoreCase(client.getSession().getUsername()) && passAlong) {
            if(passAlongModifier != null) {
                Pair<HashMap<String, String>, String> modified = passAlongModifier.getModifiedMessageToPassAlong();
                headerFields = modified.getLeft();
                message = modified.getRight();
            }

            String fullMessage = encodeHeader(headerFields, true) + message;
            if(NoChatReportsUtil.isModAvailable() && message != null && !message.isBlank()) {
                // (Re-)encrypt if suitable

                if(!NoChatReportsUtil.supportsDecryptDetailed() && NoChatReportsUtil.isEnabled()) {
                    // Does not support finding out the details of how a message got delivered,
                    // since using official mod. Encrypting with own defaults if mod is active.
                    fullMessage = encodeHeader(headerFields, true) + NoChatReportsUtil.encrypt(message);
                }
                if(NoChatReportsUtil.supportsDecryptDetailed() && ncrDecryptionInfo != null) {
                    // Received message was encrypted with given details. Use same params for sending along
                    // Prefer to compress if sender also compressed but use own defaults (all compressions should work rn on the mod)
                    fullMessage = NoChatReportsUtil.encryptAndCompress(ncrDecryptionInfo.keyIndex(), ncrDecryptionInfo.encapsulation(),
                            "msg " + nextMember + " " + encodeHeader(headerFields, true), message,
                            ncrDecryptionInfo.compression() != null ? NoChatReportsUtil.CompressionPolicy.Preferred : NoChatReportsUtil.CompressionPolicy.Never,
                            null /* = Auto Determine */);
                }
            }
            if(!RingConfig.getInstance().debug)
                // Hide sent message confirmation
                ClientMod.INSTANCE.hideSentFullMessages.put(fullMessage.startsWith("msg ") ? fullMessage.substring(("msg " + nextMember + " ").length()).trim() : fullMessage.trim(), System.currentTimeMillis() + 5000);
            if(fullMessage.startsWith("msg "))
                ChatUtil.sendCommand(client, fullMessage);
            else
                ChatUtil.sendCommand(client, "msg " + nextMember + " " + fullMessage);
            System.out.println("[RingMessage] Passed Message along to " + nextMember);
            return true;
        }else if (passAlong) {
            if(RingConfig.getInstance().debug && client.player != null)
                client.player.sendMessage(Text.of(ClientMod.PREFIX + "§oYour message was received by everyone."), false);
            int messageHash = hashOfMessage(headerFields, message);
            if(ClientMod.INSTANCE.expectConfirmationUntil.containsKey(messageHash)) {
                MainMod.LOGGER.info("[RingMessage] Message with Message Hash" + messageHash + " was successfully sent to everyone!");
                ClientMod.INSTANCE.expectConfirmationUntil.remove(messageHash); // Prevent timeout
            } else {
                MainMod.LOGGER.info("[RingMessage] Message with Message Hash" + messageHash + " was successfully sent to everyone!");
                if(client.player != null)
                    client.player.sendMessage(Text.of(ClientMod.PREFIX + "§eRe-received a message that was not expected (confirmation already timed out or forged message)!"), false);
            }

            // Got confirmation from last user in ring!
            return false;
        }else {
            return true;
        }
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

    public static String generateHashModification() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 6; i++)
            builder.append(letterDict.charAt(RNG.nextInt(letterDict.length())));
        return builder.toString();
    }

    public static String generateMessageId() {
        return generateHashModification();
    }

    public static Ring.OrderedMemberRing createNewRing(MinecraftClient client) {
        HashSet<String> onlineMembers = Ring.getOnlineMembers(client, RingConfig.getInstance().ringMembers);
        //System.out.println("Online Members: " + StringUtil.joinCommaSeparated(onlineMembers));
        Ring.OrderedMemberRing ring = null;
        int attempts = 0;

        while(ring == null) {
            attempts++;
            String hashModification = generateHashModification();
            ring = Ring.OrderedMemberRing.ofOnlineMembers(onlineMembers, client.getSession().getUsername(), hashModification);
            if(ring == null)
                System.err.println("[RingMessage] Failed to create ring! Likely hash collision! (Attempt " + attempts + ")");
            if(attempts >= 10) {
                System.err.println("[RingMessage] Ran out of attempts, creating a ring!");
                return null;
            }
        }

        return ring;
    }

    public static void sendNewRingChatMessage(MinecraftClient client, Ring.OrderedMemberRing ring, String message) {
        boolean didEncrypt = sendNewRawRingMessage(client, ring, null, message);

        if(client.player != null) {
            String encryptionTooltip = null;
            if(didEncrypt)
                    encryptionTooltip = """
                    §a🔒 §a§nEncrypted with No Chat Reports§a 🔒

                    §aYou encrypted this message with
                    §ayour current settings.""";
            showChatMessage(client.player, client.getSession().getUsername(), encryptionTooltip, message);
        }
    }

    public static void sendNewRingSyncMembersMessage(MinecraftClient client, Ring.OrderedMemberRing ring, HashSet<String> newMembers) {
        HashMap<String, String> headerFields = new HashMap<>();
        headerFields.put("syncmembers", StringUtil.join(",", ",", newMembers));
        sendNewRawRingMessage(client, ring, headerFields, null);
    }

    public static void sendNewRingBasicTestMessage(MinecraftClient client, String targetMember) {
        HashSet<String> twoMembers = new HashSet<>();
        twoMembers.add(client.getSession().getUsername());
        twoMembers.add(targetMember);
        Ring.OrderedMemberRing twoMemberRing = Ring.OrderedMemberRing.ofOnlineMembers(twoMembers, null, generateHashModification());

        HashMap<String, String> headerFields = new HashMap<>();
        headerFields.put("basictest", "");
        headerFields.put("id", generateMessageId());
        sendNewRawRingMessage(client, twoMemberRing, headerFields, null);
    }

    /**
     * @return Whether message got encrypted
     */
    public static boolean sendNewRawRingMessage(MinecraftClient client, Ring.OrderedMemberRing ring, HashMap<String, @Nullable String> extraHeaderFields, @Nullable String message) {
        if(message == null) message = "";
        if(extraHeaderFields == null) extraHeaderFields = new HashMap<>();
        String nextMember = ring.after(client.getSession().getUsername());

        HashMap<String, String> headerFields = new HashMap<>();
        headerFields.put("s", client.getSession().getUsername());
        if(ring.getUsedHashModification() != null)
            headerFields.put("h", ring.getUsedHashModification());
        headerFields.putAll(extraHeaderFields);

        boolean didEncrypt = false;
        String fullMessage = encodeHeader(headerFields, true) + message;
        if(NoChatReportsUtil.isModAvailable() && (NoChatReportsUtil.isEnabled() || RingConfig.getInstance().alwaysEncrypt) && message != null && !message.isBlank()) {
            fullMessage = encodeHeader(headerFields, true) + NoChatReportsUtil.encrypt(message);
            didEncrypt = true;
        }
        if(!RingConfig.getInstance().debug)
            // Hide sent message confirmation
            ClientMod.INSTANCE.hideSentFullMessages.put(fullMessage.trim(), System.currentTimeMillis() + 5000);
        int messageHash = hashOfMessage(headerFields, message);
        ChatUtil.sendCommand(client, "msg " + nextMember + " " + fullMessage);

        System.out.println("[RingMessage] Sent a new " + (didEncrypt ? ", encrypted " : "") + "message with Message Hash " + messageHash + ": " + encodeHeader(headerFields, true) + message);
        ClientMod.INSTANCE.expectConfirmationUntil.put(messageHash, System.currentTimeMillis() + 1000L + ring.getRing().length * 300L);
        return didEncrypt;
    }

    public static int hashOfMessage(HashMap<String, String> headerFields, String message) {
        if(headerFields.containsKey("id"))
            return headerFields.get("id").hashCode();

        if(message == null) message = "";
        message = message.trim();

        StringBuilder hashStringBuilder = new StringBuilder(message + "\0");

        ArrayList<String> headerKeys = new ArrayList<>(headerFields.keySet());
        Collections.sort(headerKeys);
        for(String key : headerKeys) {
            hashStringBuilder.append(key);
            hashStringBuilder.append('\0');
            hashStringBuilder.append(headerFields.get(key));
            hashStringBuilder.append('\0');
        }

        return hashStringBuilder.toString().hashCode();
    }
}
