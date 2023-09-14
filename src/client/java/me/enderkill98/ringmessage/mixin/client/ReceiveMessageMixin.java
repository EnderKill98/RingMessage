package me.enderkill98.ringmessage.mixin.client;

import me.enderkill98.ringmessage.ClientMod;
import me.enderkill98.ringmessage.config.RingConfig;
import me.enderkill98.ringmessage.util.NoChatReportsUtil;
import me.enderkill98.ringmessage.util.RingMessage;
import me.enderkill98.ringmessage.util.StringUtil;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.Optional;

@Mixin(ChatHud.class)
public class ReceiveMessageMixin {

    private boolean hideOwnSentMessage(String messageStr) {
        String content = null;
        if(messageStr.startsWith("[me -> ") && messageStr.contains("]")) {
            content = StringUtil.join(" ", " ", Arrays.stream(messageStr.split(" ")).skip(3));
        }
        if(messageStr.startsWith("You whisper to ") && messageStr.contains(":")) {
            content = StringUtil.join(" ", " ", Arrays.stream(messageStr.split(" ")).skip(4));
        }

        return content != null && ClientMod.INSTANCE.hideSentFullMessages.containsKey(content.trim());
    }

    @Inject(at = @At("HEAD"), method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", cancellable = true)
    public void addMessage(Text message, @Nullable MessageSignatureData signature, @Nullable MessageIndicator indicator, CallbackInfo info) {
        String messageStr = message.getString();
        if(hideOwnSentMessage(messageStr)) {
            // Hide own sent message
            info.cancel();
            return;
        }

        System.out.println("Test: " + messageStr);
        String sender = null, content = null;
        if(messageStr.startsWith("[") && messageStr.contains(" -> me]")) {
            sender = messageStr.split(" ")[0].substring(1);
            content = StringUtil.join(" ", " ", Arrays.stream(messageStr.split(" ")).skip(3));
        }
        if(messageStr.contains(" whispers to you: ")) {
            sender = messageStr.split(" ")[0];
            content = StringUtil.join(" ", " ", Arrays.stream(messageStr.split(" ")).skip(4));
        }
        if(sender == null || content == null) return;
        System.out.println("Sender: \"" + sender + "\", Content: \"" + content + "\"");

        NoChatReportsUtil.DetailedDecryptionInfo ncrDecryptionInfo = null;
        if(NoChatReportsUtil.isModAvailable()) {
            if(NoChatReportsUtil.supportsDecryptDetailed()) {
                Optional<NoChatReportsUtil.DetailedDecryptionInfo> decryptionInfo = NoChatReportsUtil.tryDecryptDetailed(content);
                if(decryptionInfo.isPresent()) {
                    content = decryptionInfo.get().decryptedText();
                    ncrDecryptionInfo = decryptionInfo.get();
                }
            }else {
                // Most likely using official mod, which doesn't have the custom decryptDetailed() method
                String maybeDecrypted = NoChatReportsUtil.decrypt(content);
                if(maybeDecrypted != null) content = maybeDecrypted;
            }
        }
        if(!content.contains("[rm")) return;
        if(RingMessage.handleReceivedMessage(sender, content, ncrDecryptionInfo)) {
            if(!RingConfig.getInstance().debug)
                info.cancel();
        }
    }
}
