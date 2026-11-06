package de.longeyes;

import com.google.gson.annotations.SerializedName;

/**
 * How the eyes come out of the head. Chosen per player and synchronised, so everybody sees the
 * style the zooming player picked.
 */
public enum EyeStyle {
	/** Eyeballs on long, thin telescoping stalks (SpongeBob-as-a-snail look). */
	@SerializedName("stalk")
	STALK,
	/** Only the eyeball comes out; its white part stretches into a long tube with the pupil at the tip. */
	@SerializedName("cartoon")
	CARTOON;

	private static final EyeStyle[] VALUES = values();

	public int id() {
		return ordinal();
	}

	public static boolean isValidId(int id) {
		return id >= 0 && id < VALUES.length;
	}

	public static EyeStyle byId(int id) {
		return isValidId(id) ? VALUES[id] : STALK;
	}

	public EyeStyle next() {
		return VALUES[(ordinal() + 1) % VALUES.length];
	}

	public String translationKey() {
		return "longeyes.style." + name().toLowerCase(java.util.Locale.ROOT);
	}
}
