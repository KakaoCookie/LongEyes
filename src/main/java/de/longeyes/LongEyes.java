package de.longeyes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;

import de.longeyes.config.EyesConfig;
import de.longeyes.network.EyeNetworking;
import de.longeyes.server.EyeSyncServer;

/**
 * Common entrypoint (runs on dedicated servers and on clients).
 * Contains no client classes so that a dedicated server never loads rendering code.
 */
public final class LongEyes implements ModInitializer {
	public static final String MOD_ID = "longeyes";
	public static final Logger LOGGER = LoggerFactory.getLogger("Long Eyes");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		EyesConfig.load();
		EyeNetworking.registerPayloadTypes();
		EyeSyncServer.init();
		LOGGER.info("Long Eyes initialised - hold C to zoom and let your eyes out!");
	}
}
