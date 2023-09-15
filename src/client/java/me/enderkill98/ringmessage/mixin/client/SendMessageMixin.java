package me.enderkill98.ringmessage.mixin.client;

import me.enderkill98.ringmessage.ClientMod;
import me.enderkill98.ringmessage.config.RingConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class SendMessageMixin {

    @Shadow public abstract String normalize(String chatText);

    @Inject(at = @At("HEAD"), method = "sendMessage(Ljava/lang/String;Z)Z", cancellable = true)
    private void sendMessage(String chatText, boolean addToHistory, CallbackInfoReturnable<Boolean> info) {
        final String origChatText = chatText;
        if (chatText.startsWith("$ ")) {
            if(RingConfig.getInstance().directUse){
                if(MinecraftClient.getInstance().player == null){
                    info.setReturnValue(true);
                    return;
                }
                chatText = chatText.substring("$ ".length());
                chatText = normalize(chatText);
                if (chatText.isEmpty()) {
                    info.setReturnValue(true);
                } else {
                    if (addToHistory) MinecraftClient.getInstance().inGameHud.getChatHud().addToMessageHistory(origChatText);

                    MinecraftClient.getInstance().player.networkHandler.sendChatMessage(chatText);

                    info.setReturnValue(true);
                }
                return;
            }
            chatText = "$rmsg send " + chatText.substring("$ ".length());
        }
        if(chatText.equalsIgnoreCase("$rmsg") || chatText.toLowerCase().startsWith("$rmsg ")
                || chatText.equalsIgnoreCase("$ringmsg") || chatText.toLowerCase().startsWith("$ringmsg ")) {

            String cmdName = chatText.contains(" ") ? chatText.substring(1, chatText.indexOf(" ")) : chatText.substring(1);
            String[] args = chatText.contains(" ") ? chatText.substring(chatText.indexOf(" ") + 1).split(" ") : new String[0];

            ClientMod.INSTANCE.ringMessageCommand.onExecute(cmdName, args);

            if (addToHistory) MinecraftClient.getInstance().inGameHud.getChatHud().addToMessageHistory(origChatText);
            info.setReturnValue(true); // Close chat screen and prevent further handling
        } else if(RingConfig.getInstance().directUse && !chatText.startsWith("/") && !chatText.startsWith(".") && !chatText.startsWith("#") && !chatText.startsWith(",") && !chatText.startsWith("+") && !chatText.startsWith("-")) {
            chatText = "send " + chatText;
            String[] args = chatText.contains(" ") ? chatText.split(" ") : new String[]{chatText};
            ClientMod.INSTANCE.ringMessageCommand.onExecute("rmsg", args);

            if (addToHistory) MinecraftClient.getInstance().inGameHud.getChatHud().addToMessageHistory(origChatText);
            info.setReturnValue(true);
        }
    }

}
