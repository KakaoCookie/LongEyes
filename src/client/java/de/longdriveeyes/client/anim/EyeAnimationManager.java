package de.longdriveeyes.client.anim;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Holds the animation state of every player whose eyes are (or were just) extended, keyed by entity id.
 * Players with retracted eyes are not stored at all, so the map is empty most of the time.
 * Only accessed from the client (render) thread.
 */
public final class EyeAnimationManager {
	private static final Int2ObjectMap<EyeAnimation> ANIMATIONS = new Int2ObjectOpenHashMap<>();

	private EyeAnimationManager() {
	}

	public static void setTarget(int entityId, float target) {
		EyeAnimation animation = ANIMATIONS.get(entityId);

		if (animation == null) {
			if (target <= 0.0f) {
				return;
			}

			animation = new EyeAnimation();
			ANIMATIONS.put(entityId, animation);
		}

		animation.setTarget(target);
	}

	public static EyeAnimation get(int entityId) {
		return ANIMATIONS.get(entityId);
	}

	public static void tick(ClientLevel level, int localPlayerId) {
		if (ANIMATIONS.isEmpty()) {
			return;
		}

		ObjectIterator<Int2ObjectMap.Entry<EyeAnimation>> iterator = ANIMATIONS.int2ObjectEntrySet().iterator();

		while (iterator.hasNext()) {
			Int2ObjectMap.Entry<EyeAnimation> entry = iterator.next();
			EyeAnimation animation = entry.getValue();
			animation.tick();

			boolean entityGone = level == null || (entry.getIntKey() != localPlayerId && level.getEntity(entry.getIntKey()) == null);

			if (animation.isFullyRetracted() || entityGone) {
				iterator.remove();
			}
		}
	}

	public static void clear() {
		ANIMATIONS.clear();
	}
}
