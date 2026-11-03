package de.longdriveeyes.client;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;

import de.longdriveeyes.client.anim.EyeAnimationManager;
import de.longdriveeyes.client.network.EyeSyncClient;
import de.longdriveeyes.client.render.EyeStalkLayer;
import de.longdriveeyes.client.zoom.ZoomController;

public final class LongDriveEyesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ZoomController.init();
		EyeSyncClient.init();

		// Attach the eye layer to every player renderer (wide + slim models). The vanilla model,
		// the player's skin and all other layers stay untouched.
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
			if (entityRenderer instanceof AvatarRenderer<?> avatarRenderer) {
				registrationHelper.register(new EyeStalkLayer(avatarRenderer));
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ZoomController.tick(client);
			EyeSyncClient.tick(client, ZoomController.getTargetEyeExtension());
			EyeAnimationManager.tick(client.level, client.player != null ? client.player.getId() : Integer.MIN_VALUE);
		});
	}
}
