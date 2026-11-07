package de.longeyes.client.zoom;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import de.longeyes.EyeMath;
import de.longeyes.EyeStyle;
import de.longeyes.LongEyes;
import de.longeyes.config.EyesConfig;

/**
 * Self-contained zoom system.
 *
 * <p>The simulation runs per client tick (20 Hz) in logarithmic zoom space; the camera reads an
 * interpolated value with the frame's partial tick. That keeps transitions smooth at any FPS and
 * completely independent of the server TPS. No Minecraft option is ever modified.
 */
public final class ZoomController {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(LongEyes.id("main"));
	public static final KeyMapping ZOOM_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.longeyes.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY));
	public static final KeyMapping STYLE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.longeyes.style", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));

	/** Fraction of the remaining (log) distance covered per tick when smoothZoom is enabled. */
	private static final double SMOOTHING = 0.4;

	private static boolean active;
	private static double targetZoom = 1.0;
	private static double logZoom;
	private static double prevLogZoom;

	private ZoomController() {
	}

	public static void init() {
		// Static initialiser registers the key mapping.
	}

	public static void tick(Minecraft client) {
		EyesConfig config = EyesConfig.get();

		while (STYLE_KEY.consumeClick()) {
			cycleStyle(client);
		}

		boolean wanted = ZOOM_KEY.isDown() && client.screen == null && client.player != null;

		if (wanted && !active) {
			active = true;
			targetZoom = Mth.clamp(config.defaultZoom, 1.0, config.maxZoom);
		} else if (!wanted && active) {
			active = false;
			targetZoom = 1.0;
		}

		prevLogZoom = logZoom;
		double targetLog = Math.log(targetZoom);

		if (config.smoothZoom) {
			logZoom += (targetLog - logZoom) * SMOOTHING;

			if (Math.abs(targetLog - logZoom) < 1.0E-3) {
				logZoom = targetLog;
			}
		} else {
			logZoom = targetLog;
			prevLogZoom = targetLog;
		}
	}

	/** Switches between the eye styles, saves the choice and shows it above the hotbar. */
	public static void cycleStyle(Minecraft client) {
		EyesConfig config = EyesConfig.get();
		EyeStyle style = config.eyeStyle.next();
		config.eyeStyle = style;
		EyesConfig.save();
		client.gui.setOverlayMessage(Component.translatable("longeyes.style.changed", Component.translatable(style.translationKey())), false);
	}

	/** Mouse wheel while zooming. Positive steps (wheel up) zoom in. */
	public static void onScroll(int steps) {
		EyesConfig config = EyesConfig.get();
		targetZoom = Mth.clamp(targetZoom * Math.pow(config.zoomStep, steps), 1.0, config.maxZoom);
	}

	public static boolean isActive() {
		return active;
	}

	/** FOV divisor for the current frame (1 = no zoom). */
	public static double getZoomDivisor(float partialTicks) {
		return Math.exp(Mth.lerp(partialTicks, prevLogZoom, logZoom));
	}

	/** Divisor of the last completed tick, used for mouse sensitivity. */
	public static double getCurrentZoomDivisor() {
		return Math.exp(logZoom);
	}

	/** Target eye extension 0..1 of the local player derived from the selected zoom level. */
	public static float getTargetEyeExtension() {
		return active ? EyeMath.zoomToExtension(targetZoom, EyesConfig.get().eyeFullZoom) : 0.0f;
	}

	public static void reset() {
		active = false;
		targetZoom = 1.0;
		logZoom = 0.0;
		prevLogZoom = 0.0;
	}
}
