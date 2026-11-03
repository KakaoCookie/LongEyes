package de.longdriveeyes.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;

import de.longdriveeyes.client.anim.EyeAnimation;
import de.longdriveeyes.client.anim.EyeAnimationManager;
import de.longdriveeyes.client.render.EyeRenderData;

/**
 * Copies the interpolated eye animation into the player's render state (extract phase of the
 * RenderState pipeline). The render layer only reads the state.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("RETURN"))
	private void longdriveeyes$extractEyes(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		EyeAnimation animation = EyeAnimationManager.get(entity.getId());

		if (animation == null || entity.isInvisible() || isFirstPersonCamera(entity)) {
			state.setData(EyeRenderData.KEY, null);
			return;
		}

		EyeRenderData data = state.getData(EyeRenderData.KEY);

		if (data == null) {
			data = new EyeRenderData();
			state.setData(EyeRenderData.KEY, data);
		}

		data.extension = animation.get(partialTicks);
		data.velocity = animation.velocity();
		data.time = entity.tickCount + partialTicks;
	}

	/** In first person the eyes must never block the view (e.g. with "show body" mods). */
	@Unique
	private static boolean isFirstPersonCamera(Avatar entity) {
		Minecraft client = Minecraft.getInstance();
		return client.getCameraEntity() == entity && client.options.getCameraType().isFirstPerson();
	}
}
