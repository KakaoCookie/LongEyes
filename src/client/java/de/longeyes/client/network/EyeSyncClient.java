package de.longeyes.client.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import de.longeyes.EyeMath;
import de.longeyes.EyeStyle;
import de.longeyes.client.anim.EyeAnimationManager;
import de.longeyes.client.zoom.ZoomController;
import de.longeyes.config.EyesConfig;
import de.longeyes.network.EyeStateC2SPayload;
import de.longeyes.network.EyeStateS2CPayload;

/**
 * Client half of the synchronisation.
 *
 * <p>Only the <em>target</em> extension is transmitted (quantised to 0..255) and only when it changes,
 * i.e. on key press, key release and per mouse wheel notch. Every receiving client runs the very same
 * spring animation towards that target, so the motion is smooth regardless of latency or packet rate.
 */
public final class EyeSyncClient {
	private static int lastSent;
	private static int lastSentStyle;
	private static ClientLevel lastLevel;

	private EyeSyncClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(EyeStateS2CPayload.TYPE, (payload, context) -> {
			int value = payload.extension();

			if (value < 0 || value > EyeMath.NET_RESOLUTION || !EyeStyle.isValidId(payload.style())) {
				return;
			}

			// Our own eyes are driven locally; ignore echoes.
			if (context.client().player != null && context.client().player.getId() == payload.entityId()) {
				return;
			}

			EyeAnimationManager.setTarget(payload.entityId(), EyeMath.fromNet(value), EyeStyle.byId(payload.style()));
		});

		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> resetSession());
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
			resetSession();
			ZoomController.reset();
		});
	}

	private static void resetSession() {
		lastSent = 0;
		lastLevel = null;
		EyeAnimationManager.clear();
	}

	/** Called every client tick after the zoom controller has been updated. */
	public static void tick(Minecraft client, float localTarget) {
		if (client.level != lastLevel) {
			// Dimension change / respawn into new level: entity ids of other players are stale and the
			// server forgot our state, so drop everything and re-send if we are still zooming.
			EyeAnimationManager.clear();
			lastLevel = client.level;
			lastSent = 0;
		}

		if (client.player == null || client.getConnection() == null) {
			return;
		}

		EyeStyle style = EyesConfig.get().eyeStyle;
		EyeAnimationManager.setTarget(client.player.getId(), localTarget, style);

		if (!EyesConfig.get().syncMultiplayer) {
			return;
		}

		int quantised = EyeMath.toNet(localTarget);
		// A style change only matters while the eyes are out.
		boolean styleChanged = quantised > 0 && style.id() != lastSentStyle;

		if ((quantised != lastSent || styleChanged) && ClientPlayNetworking.canSend(EyeStateC2SPayload.TYPE)) {
			ClientPlayNetworking.send(new EyeStateC2SPayload(quantised, style.id()));
			lastSent = quantised;
			lastSentStyle = style.id();
		}
	}
}
