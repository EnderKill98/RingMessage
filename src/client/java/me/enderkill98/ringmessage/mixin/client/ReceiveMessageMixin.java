package me.enderkill98.ringmessage.mixin.client;

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

@Mixin(ChatHud.class)
public class ReceiveMessageMixin {

    @Inject(at = @At("HEAD"), method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", cancellable = true)
    public void addMessage(Text message, @Nullable MessageSignatureData signature, @Nullable MessageIndicator indicator, CallbackInfo info) {
        String messageStr = message.getString();
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

        if(NoChatReportsUtil.isModAvailable()) {
            String maybeDecrypted = NoChatReportsUtil.decrypt(content);
            if(maybeDecrypted != null) content = maybeDecrypted;
        }
        if(!content.contains("[rm")) return;
        if(RingMessage.handleReceivedMessage(sender, content))
            info.cancel();
    }
}
