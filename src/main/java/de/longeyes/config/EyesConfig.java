package de.longeyes.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import net.fabricmc.loader.api.FabricLoader;

import de.longeyes.EyeStyle;
import de.longeyes.LongEyes;

/**
 * JSON configuration stored in {@code config/longeyes.json}.
 * Invalid values are clamped into sane ranges after loading and written back.
 */
public final class EyesConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String FILE_NAME = LongEyes.MOD_ID + ".json";

	private static volatile EyesConfig instance = new EyesConfig();

	// ---- Zoom ----
	/** Maximum zoom factor (1..100). */
	public double maxZoom = 20.0;
	/** Zoom factor applied when the zoom key is pressed. */
	public double defaultZoom = 2.0;
	/** Multiplicative change per mouse wheel notch (1.25 = +25 % per notch). */
	public double zoomStep = 1.25;
	/** Smooth (interpolated) camera transitions. If false the FOV jumps instantly. */
	public boolean smoothZoom = true;
	/** Reduce mouse sensitivity proportional to the zoom factor. */
	public boolean reduceSensitivity = true;

	// ---- Eyes ----
	/** "stalk" = eyeballs on thin stalks, "cartoon" = stretched eyeball (switch in game with K). */
	public EyeStyle eyeStyle = EyeStyle.STALK;
	/** Maximum eye stalk length in blocks at maximum zoom. */
	public double maxEyeExtension = 1.5;
	/** Spring stiffness while extending (0.02..1, higher = faster). */
	public double eyeExtensionSpeed = 0.18;
	/** Spring stiffness while retracting (0.02..1, higher = faster). */
	public double eyeRetractionSpeed = 0.25;
	/** Edge length of an eyeball in blocks. */
	public double eyeSize = 0.22;
	/** Thickness of the eye stalks in blocks. */
	public double eyeStalkThickness = 0.06;
	/** Cartoon wobble / sag of long eye stalks. */
	public boolean eyeWobble = true;

	// ---- Multiplayer ----
	/** Client: send own eye state. Server: relay eye states to other players. */
	public boolean syncMultiplayer = true;

	public static EyesConfig get() {
		return instance;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	public static void load() {
		Path path = path();
		EyesConfig loaded = null;

		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, EyesConfig.class);
			} catch (IOException | JsonParseException e) {
				LongEyes.LOGGER.warn("Could not read {}, using defaults: {}", path, e.getMessage());
			}
		}

		if (loaded == null) {
			loaded = new EyesConfig();
		}

		loaded.validate();
		instance = loaded;
		save();
	}

	public static void save() {
		Path path = path();

		try {
			Files.createDirectories(path.getParent());

			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			LongEyes.LOGGER.warn("Could not write {}: {}", path, e.getMessage());
		}
	}

	private void validate() {
		if (eyeStyle == null) {
			eyeStyle = EyeStyle.STALK;
		}

		maxZoom = clamp(maxZoom, 1.0, 100.0, 20.0);
		defaultZoom = clamp(defaultZoom, 1.0, maxZoom, Math.min(2.0, maxZoom));
		zoomStep = clamp(zoomStep, 1.01, 4.0, 1.25);
		maxEyeExtension = clamp(maxEyeExtension, 0.1, 8.0, 1.5);
		eyeExtensionSpeed = clamp(eyeExtensionSpeed, 0.02, 1.0, 0.18);
		eyeRetractionSpeed = clamp(eyeRetractionSpeed, 0.02, 1.0, 0.25);
		eyeSize = clamp(eyeSize, 0.05, 1.0, 0.22);
		eyeStalkThickness = clamp(eyeStalkThickness, 0.01, 0.5, 0.06);
	}

	private static double clamp(double value, double min, double max, double fallback) {
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			return fallback;
		}

		return Math.max(min, Math.min(max, value));
	}
}
