package de.longeyes.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class EyeNetworking {
	private EyeNetworking() {
	}

	/** Must run on both logical sides before any connection is opened. */
	public static void registerPayloadTypes() {
		PayloadTypeRegistry.serverboundPlay().register(EyeStateC2SPayload.TYPE, EyeStateC2SPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(EyeStateS2CPayload.TYPE, EyeStateS2CPayload.CODEC);
	}
}
