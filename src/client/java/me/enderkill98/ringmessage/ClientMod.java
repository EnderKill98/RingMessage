package me.enderkill98.ringmessage;

import net.fabricmc.api.ClientModInitializer;

public class ClientMod implements ClientModInitializer {

	public static ClientMod INSTANCE = null;
	public static String PREFIX = "§8[§aRingMsg§8] §f";

	public RingMessageCommand ringMessageCommand = new RingMessageCommand();

	@Override
	public void onInitializeClient() {
		INSTANCE = this;
	}
}