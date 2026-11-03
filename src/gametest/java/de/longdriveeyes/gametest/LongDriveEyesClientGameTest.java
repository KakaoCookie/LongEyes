package de.longdriveeyes.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.MixinEnvironment;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import de.longdriveeyes.EyeMath;
import de.longdriveeyes.client.anim.EyeAnimation;
import de.longdriveeyes.client.anim.EyeAnimationManager;
import de.longdriveeyes.client.zoom.ZoomController;
import de.longdriveeyes.network.EyeStateS2CPayload;
import de.longdriveeyes.server.EyeSyncServer;

/**
 * End-to-end test on a real dedicated server with a real client:
 * key handling, mouse wheel, FOV, hotbar protection, menu handling, client -> server sync,
 * server -> client sync onto another avatar (a mannequin stands in for a second player),
 * rendering (screenshot pixel comparison) and reset on disconnect.
 */
public final class LongDriveEyesClientGameTest implements FabricClientGameTest {
	private int passed;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		Properties props = new Properties();
		props.setProperty("level-type", "minecraft:flat");
		props.setProperty("generate-structures", "false");
		props.setProperty("spawn-protection", "0");

		int mannequinId;

		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
			try (TestServerConnection connection = server.connect()) {
				connection.getClientLevel().waitForChunksRender();

				String name = ctx.computeOnClient(client -> client.getGameProfile().name());
				UUID uuid = ctx.computeOnClient(client -> client.player.getUUID());
				int ownId = ctx.computeOnClient(client -> client.player.getId());

				server.runCommand("time set noon");
				server.runCommand("weather clear");
				server.runCommand("gamemode creative " + name);
				server.runCommand("tp " + name + " 0.5 -60 4.5 180 0");
				server.runCommand("summon minecraft:mannequin 0.5 -60 0.5 {Rotation:[-90f,0f]}");
				ctx.waitFor(client -> findMannequin(client) != null, 200);
				mannequinId = ctx.computeOnClient(client -> findMannequin(client).getId());
				ctx.waitTicks(20);
				log("world ready, player=" + name + " id=" + ownId + " mannequin id=" + mannequinId);

				// ---- 1. All mixins apply (defaultRequire = 1 -> a missing injection point throws) ----
				MixinEnvironment.getCurrentEnvironment().audit();
				pass("all mixins applied (Camera.calculateFov, MouseHandler.onScroll/turnPlayer, AvatarRenderer.extractRenderState)");

				check(ZoomController.ZOOM_KEY.getDefaultKey().getValue() == GLFW.GLFW_KEY_C, "zoom key defaults to C and is a registered, rebindable KeyMapping");

				// ---- 2. Zoom key ----
				ctx.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
				check(!ctx.computeOnClient(client -> ZoomController.isActive()), "zoom inactive before pressing C");
				ctx.getInput().holdKey(ZoomController.ZOOM_KEY);
				ctx.waitTicks(15);
				checkNear(divisor(ctx), 2.0, 0.05, "C held -> default zoom 2x");
				checkNear(serverState(ctx, server, uuid), expectedNet(2.0), 0, "server received eye state for 2x");

				// ---- 3. Mouse wheel ----
				for (int i = 0; i < 5; i++) {
					ctx.getInput().scroll(1.0);
					ctx.waitTick();
				}

				ctx.waitTicks(20);
				double expected = 2.0 * Math.pow(1.25, 5);
				checkNear(divisor(ctx), expected, 0.08, "5 wheel notches up -> zoom " + String.format("%.2f", expected) + "x");
				check(ctx.computeOnClient(client -> client.player.getInventory().getSelectedSlot()) == 0, "hotbar slot unchanged while zooming");
				checkNear(serverState(ctx, server, uuid), expectedNet(expected), 0, "server state follows wheel zoom");
				checkNear(localEye(ctx, ownId), EyeMath.zoomToExtension(expected, 20.0), 0.02, "own eye animation reached target");

				for (int i = 0; i < 25; i++) {
					ctx.getInput().scroll(1.0);
					ctx.waitTick();
				}

				ctx.waitTicks(25);
				checkNear(divisor(ctx), 20.0, 0.3, "zoom clamps at maxZoom 20x");
				checkNear(serverState(ctx, server, uuid), 255, 0, "server state at maximum (255)");
				ctx.takeScreenshot("01_first_person_zoom_20x");

				for (int i = 0; i < 3; i++) {
					ctx.getInput().scroll(-1.0);
					ctx.waitTick();
				}

				ctx.waitTicks(20);
				checkNear(divisor(ctx), 20.0 / Math.pow(1.25, 3), 0.3, "3 notches down -> zoom out to ~10.2x");

				// ---- 4. Menus disable zoom ----
				ctx.getInput().pressKey(options -> options.keyInventory);
				ctx.waitTicks(5);
				check(!ctx.computeOnClient(client -> ZoomController.isActive()), "zoom disabled while a menu is open");
				ctx.waitTicks(5);
				checkNear(serverState(ctx, server, uuid), 0, 0, "server told eyes retract when menu opens");
				ctx.setScreen(() -> null);
				ctx.waitTicks(5);
				check(ctx.computeOnClient(client -> ZoomController.isActive()), "zoom resumes after closing the menu while C is still held");

				// ---- 5. Release ----
				ctx.getInput().releaseKey(ZoomController.ZOOM_KEY);
				ctx.waitTicks(20);
				check(!ctx.computeOnClient(client -> ZoomController.isActive()), "C released -> zoom off");
				checkNear(divisor(ctx), 1.0, 0.01, "FOV back to normal");
				checkNear(serverState(ctx, server, uuid), 0, 0, "server state back to 0");
				ctx.waitTicks(40);
				check(ctx.computeOnClient(client -> EyeAnimationManager.get(ownId) == null), "own eyes fully retracted and animation state released");

				ctx.getInput().scroll(-1.0);
				ctx.waitTick();
				check(ctx.computeOnClient(client -> client.player.getInventory().getSelectedSlot()) != 0, "mouse wheel changes hotbar again after zoom");

				// ---- 6. Own eyes visible in front third person ----
				setCamera(ctx, CameraType.THIRD_PERSON_FRONT);
				ctx.waitTicks(5);
				Path ownBefore = ctx.takeScreenshot("02_third_person_front_no_zoom");
				ctx.getInput().holdKey(ZoomController.ZOOM_KEY);
				ctx.waitTicks(40);
				Path ownAfter = ctx.takeScreenshot("03_third_person_front_zoom_2x");
				check(changedPixels(ownBefore, ownAfter) > 0.002, "own eyes rendered in front third-person view (screenshot differs)");
				ctx.getInput().releaseKey(ZoomController.ZOOM_KEY);
				ctx.waitTicks(40);
				setCamera(ctx, CameraType.FIRST_PERSON);

				// ---- 7. Server -> client sync onto another avatar + rendering ----
				Path baseline = ctx.takeScreenshot("10_mannequin_side_0");
				float[] levels = {0.23f, 0.54f, 0.77f, 1.0f};
				String[] labels = {"2x", "5x", "10x", "20x"};

				for (int i = 0; i < levels.length; i++) {
					sendToClient(server, mannequinId, EyeMath.toNet(levels[i]));
					ctx.waitTicks(40);
					checkNear(eye(ctx, mannequinId), levels[i], 0.02, "remote avatar eyes at level for " + labels[i]);
					Path shot = ctx.takeScreenshot("1" + (i + 1) + "_mannequin_side_" + labels[i]);
					double changed = changedPixels(baseline, shot);
					log(String.format("   changed pixels vs. retracted: %.2f %%", changed * 100));
					check(changed > 0.001, "remote eyes visibly rendered at " + labels[i]);
				}

				server.runCommand("tp " + name + " 4.5 -60 0.5 90 0");
				ctx.waitTicks(10);
				ctx.takeScreenshot("15_mannequin_front_20x");

				sendToClient(server, mannequinId, 0);
				ctx.waitTicks(40);
				check(ctx.computeOnClient(client -> EyeAnimationManager.get(mannequinId) == null), "remote eyes retract fully on 0");

				// Invalid values from the server are ignored by the client.
				sendToClient(server, mannequinId, 9999);
				ctx.waitTicks(5);
				check(ctx.computeOnClient(client -> EyeAnimationManager.get(mannequinId) == null), "out-of-range network value rejected");

				// ---- 8. Disconnect resets state ----
				ctx.getInput().holdKey(ZoomController.ZOOM_KEY);
				sendToClient(server, mannequinId, 200);
				ctx.waitTicks(10);
			}

			ctx.getInput().releaseKey(ZoomController.ZOOM_KEY);
			ctx.waitTicks(5);
			int id = mannequinId;
			check(ctx.computeOnClient(client -> EyeAnimationManager.get(id) == null && !ZoomController.isActive()
					&& ZoomController.getZoomDivisor(1.0f) == 1.0), "disconnect clears all eye states and zoom");
		}

		log("ALL " + passed + " CHECKS PASSED");
	}

	private static Entity findMannequin(Minecraft client) {
		if (client.level == null) {
			return null;
		}

		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof Avatar && !(entity instanceof Player)) {
				return entity;
			}
		}

		return null;
	}

	private static void sendToClient(TestDedicatedServerContext server, int entityId, int value) {
		server.runOnServer(s -> {
			for (ServerPlayer player : s.getPlayerList().getPlayers()) {
				ServerPlayNetworking.send(player, new EyeStateS2CPayload(entityId, value));
			}
		});
	}

	private static int serverState(ClientGameTestContext ctx, TestDedicatedServerContext server, UUID uuid) {
		ctx.waitTicks(3);
		return server.computeOnServer(s -> EyeSyncServer.getState(uuid));
	}

	private static int expectedNet(double zoom) {
		return EyeMath.toNet(EyeMath.zoomToExtension(zoom, 20.0));
	}

	private static double divisor(ClientGameTestContext ctx) {
		return ctx.computeOnClient(client -> ZoomController.getZoomDivisor(1.0f));
	}

	private static double localEye(ClientGameTestContext ctx, int id) {
		return eye(ctx, id);
	}

	private static double eye(ClientGameTestContext ctx, int id) {
		return ctx.computeOnClient(client -> {
			EyeAnimation animation = EyeAnimationManager.get(id);
			return animation == null ? -1.0 : animation.get(1.0f);
		});
	}

	private static void setCamera(ClientGameTestContext ctx, CameraType type) {
		ctx.runOnClient(client -> client.options.setCameraType(type));
	}

	/** Fraction of pixels whose colour differs noticeably between two screenshots. */
	private static double changedPixels(Path a, Path b) {
		try (InputStream inA = Files.newInputStream(a); InputStream inB = Files.newInputStream(b);
				NativeImage imgA = NativeImage.read(inA); NativeImage imgB = NativeImage.read(inB)) {
			int w = Math.min(imgA.getWidth(), imgB.getWidth());
			int h = Math.min(imgA.getHeight(), imgB.getHeight());
			long changed = 0;

			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int p = imgA.getPixel(x, y);
					int q = imgB.getPixel(x, y);
					int d = Math.abs((p >> 16 & 255) - (q >> 16 & 255)) + Math.abs((p >> 8 & 255) - (q >> 8 & 255)) + Math.abs((p & 255) - (q & 255));

					if (d > 40) {
						changed++;
					}
				}
			}

			return changed / (double) ((long) w * h);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void check(boolean condition, String message) {
		if (!condition) {
			log("FAIL " + message);
			throw new AssertionError(message);
		}

		pass(message);
	}

	private void checkNear(double actual, double expected, double tolerance, String message) {
		check(Math.abs(actual - expected) <= tolerance, message + " (expected " + expected + ", got " + actual + ")");
	}

	private void pass(String message) {
		passed++;
		log("PASS " + message);
	}

	private static void log(String message) {
		System.out.println("[LDE-TEST] " + message);
	}
}
