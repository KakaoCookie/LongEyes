package de.longeyes.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

import de.longeyes.EyeStyle;

/**
 * Snapshot of everything the eye layer needs, extracted into the player's render state during
 * {@code extractRenderState}. The layer itself never touches the entity (RenderState pipeline).
 */
public final class EyeRenderData {
	public static final RenderStateDataKey<EyeRenderData> KEY = RenderStateDataKey.create(() -> "longeyes:eyes");

	/** Interpolated normalised extension (0..~1.15 incl. spring overshoot). */
	public float extension;
	/** Interpolated spring velocity (per tick), drives the sag of the stalks. */
	public float velocity;
	/** Eye style from the viewing client's own setting (applies to all players it sees). */
	public EyeStyle style = EyeStyle.STALK;
	/** Continuous time in ticks for the idle wobble. */
	public float time;
}
