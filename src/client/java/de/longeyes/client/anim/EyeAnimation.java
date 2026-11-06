package de.longeyes.client.anim;

import de.longeyes.EyeStyle;
import de.longeyes.config.EyesConfig;

/**
 * Damped spring that drives one player's eye extension (normalised, 0 = retracted, 1 = maximum).
 *
 * <p>Simulated per client tick; rendering interpolates between the last two ticks with the partial
 * tick, which yields smooth motion at any frame rate. The slightly under-damped spring gives the
 * cartoon "boing" overshoot (ease-out-back) when the eyes shoot out or snap back.
 */
public final class EyeAnimation {
	/** Slightly under-damped while extending: ~5 % cartoon overshoot ("boing"). */
	private static final float DAMPING_EXTEND = 0.5f;
	/** Nearly critically damped while retracting: the eyes glide back in without slamming. */
	private static final float DAMPING_RETRACT = 0.35f;
	private static final float REST_EPSILON = 0.002f;

	private float target;
	private float value;
	private float prevValue;
	private float velocity;
	private boolean extending = true;
	private EyeStyle style = EyeStyle.STALK;

	public EyeStyle style() {
		return style;
	}

	public void setStyle(EyeStyle style) {
		this.style = style;
	}

	public void setTarget(float target) {
		float clamped = Math.max(0.0f, Math.min(1.0f, target));

		if (clamped != this.target) {
			this.extending = clamped > this.value;
			this.target = clamped;
		}
	}

	public float target() {
		return target;
	}

	public void tick() {
		prevValue = value;

		if (isAtRest()) {
			value = target;
			velocity = 0.0f;
			return;
		}

		EyesConfig config = EyesConfig.get();
		float stiffness = (float) (extending ? config.eyeExtensionSpeed : config.eyeRetractionSpeed);
		float damping = extending ? DAMPING_EXTEND : DAMPING_RETRACT;

		velocity = velocity * damping + (target - value) * stiffness;
		value += velocity;

		// Never poke out of the back of the head and never overshoot absurdly far.
		if (value < 0.0f) {
			value = 0.0f;
			velocity = 0.0f;
		} else if (value > 1.3f) {
			value = 1.3f;
			velocity = 0.0f;
		}

		if (Math.abs(target - value) < REST_EPSILON && Math.abs(velocity) < REST_EPSILON) {
			value = target;
			velocity = 0.0f;
		}
	}

	public boolean isAtRest() {
		return value == target && velocity == 0.0f && prevValue == value;
	}

	public boolean isFullyRetracted() {
		return target == 0.0f && isAtRest();
	}

	public float get(float partialTicks) {
		return prevValue + (value - prevValue) * partialTicks;
	}

	/** Per-tick velocity, used for the cartoon sag/wobble. */
	public float velocity() {
		return velocity;
	}
}
