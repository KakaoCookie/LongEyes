package de.longeyes;

/**
 * Shared math helpers for converting between zoom factors and the normalised eye extension.
 */
public final class EyeMath {
	/** Network resolution of the eye extension (0..255 = one unsigned byte of information). */
	public static final int NET_RESOLUTION = 255;

	private EyeMath() {
	}

	/**
	 * Maps a zoom factor onto the normalised eye extension 0..1.
	 *
	 * <p>A logarithmic curve is used because zoom is perceived multiplicatively:
	 * 1x = 0.00, 2x = 0.23, 5x = 0.54, 10x = 0.77, 15x = 0.90, 20x = 1.00 (with fullZoom 20).
	 * Above {@code fullZoom} the eyes stay at their maximum length (e.g. 20x..40x).
	 */
	public static float zoomToExtension(double zoom, double fullZoom) {
		if (zoom <= 1.0 || fullZoom <= 1.0) {
			return 0.0f;
		}

		double n = Math.log(zoom) / Math.log(fullZoom);
		return (float) Math.max(0.0, Math.min(1.0, n));
	}

	public static int toNet(float extension) {
		return Math.round(Math.max(0.0f, Math.min(1.0f, extension)) * NET_RESOLUTION);
	}

	public static float fromNet(int value) {
		return Math.max(0, Math.min(NET_RESOLUTION, value)) / (float) NET_RESOLUTION;
	}
}
