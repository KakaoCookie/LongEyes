package de.longeyes.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.longeyes.LongEyes;

/**
 * Server -> client: target eye extension (0..255) of the player with the given entity id.
 */
public record EyeStateS2CPayload(int entityId, int extension) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<EyeStateS2CPayload> TYPE = new CustomPacketPayload.Type<>(LongEyes.id("eye_state_s2c"));
	public static final StreamCodec<RegistryFriendlyByteBuf, EyeStateS2CPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, EyeStateS2CPayload::entityId,
			ByteBufCodecs.VAR_INT, EyeStateS2CPayload::extension,
			EyeStateS2CPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
