package de.longeyes.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.longeyes.LongEyes;

/**
 * Client -> server: the sender's target eye extension (0..255) and eye style id.
 * Only sent when the quantised value changes (key press/release or a mouse wheel notch).
 */
public record EyeStateC2SPayload(int extension, int style) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<EyeStateC2SPayload> TYPE = new CustomPacketPayload.Type<>(LongEyes.id("eye_state_c2s"));
	public static final StreamCodec<RegistryFriendlyByteBuf, EyeStateC2SPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, EyeStateC2SPayload::extension,
			ByteBufCodecs.VAR_INT, EyeStateC2SPayload::style,
			EyeStateC2SPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
