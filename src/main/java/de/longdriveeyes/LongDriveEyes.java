package de.longdriveeyes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;

import de.longdriveeyes.config.EyesConfig;
import de.longdriveeyes.network.EyeNetworking;
import de.longdriveeyes.server.EyeSyncServer;

/**
 * Common entrypoint (runs on dedicated servers and on clients).
 * Contains no client classes so that a dedicated server never loads rendering code.
 */
public final class LongDriveEyes implements ModInitializer {
	public static final String MOD_ID = "longdriveeyes";
	public static final Logger LOGGER = LoggerFactory.getLogger("Long Drive Eyes");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		EyesConfig.load();
		EyeNetworking.registerPayloadTypes();
		EyeSyncServer.init();
		LOGGER.info("Long Drive Eyes initialised - hold C to zoom and let your eyes out!");
	}
}
