package me.enderkill98.ringmessage;

import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

public class Ring {

    public static class OrderedMemberRing {

        public static @Nullable OrderedMemberRing ofOnlineMembers(HashSet<String> onlineMembers, @Nullable String ownName, @Nullable String hashModification) {
            HashMap<Integer, String> hashToMember = new HashMap<>();
            for(String member : onlineMembers) {
                int hash = hashOf(member, hashModification);
                if(hashToMember.containsKey(hash)) {
                    // Hash collision, can't determine next
                    return null;
                }
                hashToMember.put(hash, member);
            }
            if(ownName != null) {
                int ownHash = hashOf(ownName, hashModification);
                if(!hashToMember.containsKey(ownHash))
                    hashToMember.put(ownHash, ownName);
            }
            ArrayList<Integer> orderedHashes = new ArrayList<>(hashToMember.keySet());
            Collections.sort(orderedHashes);

            ArrayList<String> orderedMembers = new ArrayList<>();
            for(int hash : orderedHashes)
                orderedMembers.add(hashToMember.get(hash));
            return new OrderedMemberRing(orderedMembers.toArray(new String[0]), hashModification);
        }

        private final String[] ring;
        private final @Nullable String usedHashModification;

        public OrderedMemberRing(String[] ring, @Nullable String usedHashModification) {
            this.ring = ring;
            this.usedHashModification = usedHashModification;
        }

        public String[] getRing() {
            return ring;
        }

        public @Nullable String getUsedHashModification() {
            return usedHashModification;
        }

        /**
         * @return -1 if not found. Else index of member in orderedMemberRing
         */
        public int indexOf(String member) {
            int memberIndex = -1;
            for(int i = 0; i < ring.length; i++) {
                if(ring[i].equalsIgnoreCase(member)) {
                    memberIndex = i;
                    break;
                }
            }
            return memberIndex;
        }

        public boolean contains(String member) {
            return indexOf(member) != -1;
        }

        public @Nullable String after(String member) {
            int memberIndex = indexOf(member);
            if(memberIndex == -1) return null; // Not found!
            return ring[Math.floorMod(memberIndex + 1, ring.length)];
        }

        public @Nullable String before(String member) {
            int memberIndex = indexOf(member);
            if(memberIndex == -1) return null; // Not found!
            return ring[Math.floorMod(memberIndex - 1, ring.length)];
        }
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

    public static int hashOf(String userName, @Nullable String hashModification) {
        userName = userName.toLowerCase();
        String hashStr = userName + (hashModification != null ? ":" + hashModification : "");
        // Create MD5 Hash
        try {
            MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(hashStr.getBytes(StandardCharsets.UTF_8));
            return (hash[0] << 3*8) | (hash[1] << 2*8) | (hash[2] << 8) | (hash[3]);
        }catch (Exception ex) {
            throw new RuntimeException("WTF, this should never happen! You don't have MD5!");
        }
    }

    public static boolean contains(HashSet<String> members, String member) {
        for(String maybeMember : members)
            if(maybeMember.equalsIgnoreCase(member)) return true;
        return false;
    }

}
