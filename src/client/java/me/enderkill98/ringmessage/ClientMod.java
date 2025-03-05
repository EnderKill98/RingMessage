package me.enderkill98.ringmessage;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ClientMod implements ClientModInitializer, ClientTickEvents.EndTick {

	public static String MOD_VERSION = null;

	public static ClientMod INSTANCE = null;
	public static String PREFIX = "§8[§aRingMsg§8] §f";

	public RingMessageCommand ringMessageCommand = new RingMessageCommand();

	public HashMap<String, Long/*Cleanup after Timestamp Millis*/> hideSentFullMessages = new HashMap<>();
	public HashMap<Integer /*Message hash*/, Long/*Cleanup after Timestamp Millis*/> expectConfirmationUntil = new HashMap<>();

	@Override
	public void onInitializeClient() {
		INSTANCE = this;

		// Find out own mod version
		Optional<ModContainer> modContainer = FabricLoader.getInstance().getModContainer("ringmessage");
		if(modContainer.isPresent())
			MOD_VERSION = modContainer.get().getMetadata().getVersion().getFriendlyString();
		else
			MOD_VERSION = "Unknown!";

		ClientTickEvents.END_CLIENT_TICK.register(this);
	}

	@Override
	public void onEndTick(MinecraftClient client) {
		// Clean up expired messages in hideSentFullMessages
		ArrayList<String> removeMessages = new ArrayList<>();
		for(Map.Entry<String, Long> entry : hideSentFullMessages.entrySet()) {
			if(entry.getValue() < System.currentTimeMillis())
				removeMessages.add(entry.getKey());
		}
		removeMessages.forEach(hideSentFullMessages::remove);

		// Find unconfirmed messages and notify user
		ArrayList<Integer> stillNotConfirmed = new ArrayList<>();
		for(Map.Entry<Integer, Long> entry : expectConfirmationUntil.entrySet()) {
			if(entry.getValue() < System.currentTimeMillis())
				stillNotConfirmed.add(entry.getKey());
		}
		for(Integer unconfirmedHash : stillNotConfirmed) {
			System.err.println("[RingMessage] A message you sent timed out (message hash: " + unconfirmedHash + ")!");
			if(client.player != null)
				client.player.sendMessage(Text.of(PREFIX + "§4A message you just sent might not have reached everyone who is online!"), false);
			expectConfirmationUntil.remove(unconfirmedHash);
		}
	}
}