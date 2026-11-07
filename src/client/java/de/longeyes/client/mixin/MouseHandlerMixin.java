package de.longeyes.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.joml.Vector2i;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.MouseHandler;

import de.longeyes.client.zoom.ZoomController;
import de.longeyes.config.EyesConfig;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	/**
	 * While zooming, the mouse wheel changes the zoom level and the hotbar slot stays unchanged.
	 * Injected right before the in-game (non-screen) scroll handling, after the wheel delta has been
	 * converted into whole notches. Screens never reach this point, so menus keep normal scrolling.
	 */
	@Inject(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSpectator()Z"), cancellable = true)
	private void longeyes$scrollZoom(CallbackInfo ci, @Local Vector2i scroll) {
		if (!ZoomController.isActive()) {
			return;
		}

		int steps = scroll.y != 0 ? scroll.y : scroll.x;

		if (steps != 0) {
			ZoomController.onScroll(steps);
		}

		ci.cancel();
	}

	/**
	 * Cinematic camera while zooming: vanilla's "smooth camera" path is used for the duration of the
	 * zoom only. The option itself ({@code Options.smoothCamera}) is never changed.
	 */
	@ModifyExpressionValue(method = "turnPlayer", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;smoothCamera:Z", opcode = Opcodes.GETFIELD))
	private boolean longeyes$cinematicWhileZooming(boolean original) {
		return original || (ZoomController.isActive() && EyesConfig.get().cinematicZoom);
	}

	/**
	 * Scales the mouse speed with the zoom: speed = zoom ^ -zoomSensitivityScaling (2x barely slower,
	 * 20x clearly slower). The sensitivity option itself is never changed.
	 */
	@ModifyExpressionValue(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0))
	private Object longeyes$zoomSensitivity(Object original) {
		double factor = ZoomController.getSensitivityFactor();

		if (factor >= 0.9999 || !(original instanceof Double sensitivity)) {
			return original;
		}

		// Vanilla turn speed is (s * 0.6 + 0.2)^3 * 8. Multiply it exactly by the factor and solve for s
		// (values below 0 are fine for the formula and avoid a hard lower limit).
		double f = (sensitivity * 0.6 + 0.2) * Math.cbrt(factor);
		return (f - 0.2) / 0.6;
	}

	/**
	 * Glide speed of the cinematic camera while zooming. Vanilla's smooth camera trails behind for a
	 * long time and gets even slower with lower sensitivity; while zooming it catches up
	 * {@code cinematicResponsiveness} times faster (smooth start, stops shortly after the mouse stops),
	 * independent of the zoom level. Vanilla's own smooth camera (F8) without zoom is unchanged.
	 */
	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/SmoothDouble;getNewDeltaValue(DD)D"), index = 1)
	private double longeyes$glideSpeed(double smoothing) {
		if (!ZoomController.isActive()) {
			return smoothing;
		}

		return smoothing * EyesConfig.get().cinematicResponsiveness / ZoomController.getSensitivityFactor();
	}
}
