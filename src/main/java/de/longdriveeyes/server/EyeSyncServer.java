package de.longdriveeyes.server;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import de.longdriveeyes.EyeMath;
import de.longdriveeyes.config.EyesConfig;
import de.longdriveeyes.network.EyeStateC2SPayload;
import de.longdriveeyes.network.EyeStateS2CPayload;

/**
 * Server side relay. Validates incoming eye states, rate-limits them and forwards them
 * (at most once per tick per player) to all players tracking the sender.
 *
 * <p>All callbacks run on the server thread, so no synchronisation is required.
 */
public final class EyeSyncServer {
	/** Max accepted packets per player within one 20 tick window. Further packets are dropped. */
	private static final int MAX_PACKETS_PER_SECOND = 40;

	private static final Map<UUID, Entry> STATES = new HashMap<>();
	private static int tickCounter;

	private EyeSyncServer() {
	}

	private static final class Entry {
		int value;
		boolean dirty;
		int packetsInWindow;
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(EyeStateC2SPayload.TYPE, (payload, context) -> receive(context.player(), payload.extension()));

		ServerTickEvents.END_SERVER_TICK.register(EyeSyncServer::tick);

		// New viewers (join, respawn, walking into range, dimension change) get the current state once.
		EntityTrackingEvents.START_TRACKING.register((tracked, viewer) -> {
			if (tracked instanceof ServerPlayer trackedPlayer) {
				Entry entry = STATES.get(trackedPlayer.getUUID());

				if (entry != null && entry.value > 0) {
					sendTo(viewer, trackedPlayer.getId(), entry.value);
				}
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> STATES.remove(listener.player.getUUID()));

		// The client re-sends its state after it noticed the level change, so the server simply forgets it.
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> STATES.remove(player.getUUID()));

		ServerLifecycleEvents.SERVER_STARTING.register(server -> STATES.clear());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> STATES.clear());
	}

	private static void receive(ServerPlayer player, int value) {
		if (!EyesConfig.get().syncMultiplayer) {
			return;
		}

		// Validation: reject anything outside the protocol range instead of trusting the client.
		if (value < 0 || value > EyeMath.NET_RESOLUTION) {
			return;
		}

		Entry entry = STATES.computeIfAbsent(player.getUUID(), uuid -> new Entry());

		if (++entry.packetsInWindow > MAX_PACKETS_PER_SECOND) {
			return; // flood protection
		}

		if (entry.value != value) {
			entry.value = value;
			entry.dirty = true;
		}
	}

	private static void tick(MinecraftServer server) {
		if (STATES.isEmpty()) {
			return;
		}

		boolean resetWindow = ++tickCounter % 20 == 0;
		Iterator<Map.Entry<UUID, Entry>> iterator = STATES.entrySet().iterator();

		while (iterator.hasNext()) {
			Map.Entry<UUID, Entry> mapEntry = iterator.next();
			Entry entry = mapEntry.getValue();

			if (resetWindow) {
				entry.packetsInWindow = 0;
			}

			if (!entry.dirty) {
				continue;
			}

			entry.dirty = false;
			ServerPlayer player = server.getPlayerList().getPlayer(mapEntry.getKey());

			if (player == null) {
				iterator.remove();
				continue;
			}

			for (ServerPlayer viewer : PlayerLookup.tracking(player)) {
				if (viewer != player) {
					sendTo(viewer, player.getId(), entry.value);
				}
			}

			if (entry.value == 0 && entry.packetsInWindow == 0) {
				iterator.remove();
			}
		}
	}

	private static void sendTo(ServerPlayer viewer, int entityId, int value) {
		if (ServerPlayNetworking.canSend(viewer, EyeStateS2CPayload.TYPE)) {
			ServerPlayNetworking.send(viewer, new EyeStateS2CPayload(entityId, value));
		}
	}
}
