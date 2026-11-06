package de.longeyes.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.Camera;

import de.longeyes.client.zoom.ZoomController;

/**
 * Applies the zoom by dividing the world FOV for the current frame. Only the returned value is
 * changed; the FOV option in the settings is never touched. The hand/HUD FOV stays untouched.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
	private float longeyes$applyZoom(float fov, @Local(name = "partialTicks", argsOnly = true) float partialTicks) {
		double divisor = ZoomController.getZoomDivisor(partialTicks);
		return divisor > 1.0001 ? (float) (fov / divisor) : fov;
	}
}
