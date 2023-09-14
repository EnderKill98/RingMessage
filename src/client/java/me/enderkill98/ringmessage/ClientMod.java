package me.enderkill98.ringmessage;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class ClientMod implements ClientModInitializer, ClientTickEvents.EndTick {

	public static ClientMod INSTANCE = null;
	public static String PREFIX = "§8[§aRingMsg§8] §f";

	public RingMessageCommand ringMessageCommand = new RingMessageCommand();

	public HashMap<String, Long/*Cleanup after Timestamp Millis*/> hideSentFullMessages = new HashMap<>();

	@Override
	public void onInitializeClient() {
		INSTANCE = this;
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
	}
}