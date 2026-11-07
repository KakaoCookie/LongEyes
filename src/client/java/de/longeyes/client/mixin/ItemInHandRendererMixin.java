package de.longeyes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;

import de.longeyes.client.zoom.ZoomController;
import de.longeyes.config.EyesConfig;

/**
 * Hides the first-person hand and held item while zooming.
 *
 * <p>Injected at the head of {@code renderHandsWithItems} instead of redirecting the call in
 * {@code GameRenderer}: Iris redirects that call itself and also invokes this method from its own
 * shader hand renderer, so this way both paths are covered without a mixin conflict.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void longeyes$hideHandWhileZooming(float partialTicks, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
			LocalPlayer player, int light, CallbackInfo ci) {
		if (ZoomController.isActive() && EyesConfig.get().hideHandWhileZooming) {
			ci.cancel();
		}
	}
}
