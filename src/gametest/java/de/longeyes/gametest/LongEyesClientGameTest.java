package de.longeyes.gametest;

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

import de.longeyes.EyeMath;
import de.longeyes.EyeStyle;
import de.longeyes.client.anim.EyeAnimation;
import de.longeyes.client.anim.EyeAnimationManager;
import de.longeyes.client.zoom.ZoomController;
import de.longeyes.config.EyesConfig;
import de.longeyes.network.EyeStateS2CPayload;
import de.longeyes.server.EyeSyncServer;

/**
 * End-to-end test on a real dedicated server with a real client:
 * key handling, mouse wheel, FOV, hotbar protection, menu handling, client -> server sync,
 * server -> client sync onto another avatar (a mannequin stands in for a second player),
 * rendering (screenshot pixel comparison) and reset on disconnect.
 */
public final class LongEyesClientGameTest implements FabricClientGameTest {
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

				// Hand is hidden while zooming (client-side view only): compare with the option switched off.
				ctx.runOnClient(client -> EyesConfig.get().hideHandWhileZooming = false);
				ctx.waitTicks(2);
				Path withHand = ctx.takeScreenshot("00_zoom_hand_option_off");
				ctx.runOnClient(client -> EyesConfig.get().hideHandWhileZooming = true);
				ctx.waitTicks(2);
				Path withoutHand = ctx.takeScreenshot("00_zoom_hand_hidden");
				double handDiff = changedPixels(withHand, withoutHand);
				log(String.format("   hand visible vs hidden while zooming: %.2f %% pixels differ", handDiff * 100));
				check(handDiff > 0.005 && handDiff < 0.3, "first-person hand disappears while zooming");

				for (int i = 0; i < 40; i++) {
					ctx.getInput().scroll(1.0);
					ctx.waitTick();
				}

				ctx.waitTicks(25);
				checkNear(divisor(ctx), 40.0, 0.5, "zoom clamps at maxZoom 40x");
				checkNear(serverState(ctx, server, uuid), 255, 0, "server state at maximum (255)");
				checkNear(localEye(ctx, ownId), 1.0, 0.01, "eyes at full length at 40x (no further than at 20x)");
				ctx.takeScreenshot("01_first_person_zoom_40x");

				for (int i = 0; i < 3; i++) {
					ctx.getInput().scroll(-1.0);
					ctx.waitTick();
				}

				ctx.waitTicks(20);
				checkNear(divisor(ctx), 40.0 / Math.pow(1.25, 3), 0.4, "3 notches down -> ~20.5x");
				checkNear(serverState(ctx, server, uuid), 255, 0, "eyes still at full length between 20x and 40x");

				for (int i = 0; i < 4; i++) {
					ctx.getInput().scroll(-1.0);
					ctx.waitTick();
				}

				ctx.waitTicks(20);
				checkNear(divisor(ctx), 40.0 / Math.pow(1.25, 7), 0.2, "4 more notches down -> ~8.4x");
				checkNear(serverState(ctx, server, uuid), expectedNet(40.0 / Math.pow(1.25, 7)), 0, "eyes retract again below 20x");

				// ---- 4. Menus disable zoom ----
				ctx.getInput().pressKey(options -> options.keyInventory);
				ctx.waitTicks(5);
				check(!ctx.computeOnClient(client -> ZoomController.isActive()), "zoom disabled while a menu is open");
				ctx.waitTicks(5);
				checkNear(serverState(ctx, server, uuid), 0, 0, "server told eyes retract when menu opens");
				ctx.setScreen(() -> null);
				ctx.waitTicks(5);
				check(ctx.computeOnClient(client -> ZoomController.isActive()), "zoom resumes after closing the menu while C is still held");

				// ---- 4b. Cinematic (smooth, gliding) mouse while zooming ----
				float[] cinematic = measureGlide(ctx);
				ctx.runOnClient(client -> EyesConfig.get().cinematicZoom = false);
				float[] direct = measureGlide(ctx);
				ctx.runOnClient(client -> EyesConfig.get().cinematicZoom = true);
				// Reference: vanilla's own "smooth camera" option (F8) under identical conditions.
				ctx.runOnClient(client -> {
					EyesConfig.get().cinematicZoom = false;
					client.options.smoothCamera = true;
				});
				float[] vanillaSmooth = measureGlide(ctx);
				ctx.runOnClient(client -> {
					client.options.smoothCamera = false;
					EyesConfig.get().cinematicZoom = true;
				});
				log(String.format("   cinematic zoom:  total %.2f deg | after 1st tick %.2f | after 0.5 s %.2f", cinematic[0], cinematic[1], cinematic[2]));
				log(String.format("   vanilla F8 cam:  total %.2f deg | after 1st tick %.2f | after 0.5 s %.2f", vanillaSmooth[0], vanillaSmooth[1], vanillaSmooth[2]));
				log(String.format("   direct:          total %.2f deg | after 1st tick %.2f | after 0.5 s %.2f", direct[0], direct[1], direct[2]));
				check(direct[0] > 0.5f, "mouse movement turns the camera while zooming");
				check(direct[1] < 0.1f * direct[0] + 0.05f, "with cinematicZoom off the camera follows directly");
				checkNear(cinematic[0], direct[0], 0.05 * direct[0], "cinematic zoom reaches the full turn (no lost movement)");
				check(cinematic[1] > 0.05f * cinematic[0], "cinematic zoom is still smooth (glides a little)");
				check(cinematic[2] < 0.05f * cinematic[0], "camera stops within ~0.5 s after the mouse stops (no long trailing)");
				check(!ctx.computeOnClient(client -> client.options.smoothCamera), "vanilla 'smooth camera' option itself is unchanged");

				// ---- 4c. Mouse speed scales gently with zoom (measured without smoothing) ----
				ctx.runOnClient(client -> EyesConfig.get().cinematicZoom = false);
				double divLow = divisor(ctx);
				float turnLow = measureGlide(ctx)[0];

				for (int i = 0; i < 10; i++) {
					ctx.getInput().scroll(1.0);
					ctx.waitTick();
				}

				ctx.waitTicks(20);
				double divHigh = divisor(ctx);
				float turnHigh = measureGlide(ctx)[0];

				for (int i = 0; i < 10; i++) {
					ctx.getInput().scroll(-1.0);
					ctx.waitTick();
				}

				ctx.waitTicks(20);
				ctx.runOnClient(client -> EyesConfig.get().cinematicZoom = true);

				// ---- 5. Release ----
				ctx.getInput().releaseKey(ZoomController.ZOOM_KEY);
				ctx.waitTicks(20);
				check(!ctx.computeOnClient(client -> ZoomController.isActive()), "C released -> zoom off");
				float turnNone = measureGlide(ctx)[0];
				log(String.format("   mouse turn: no zoom %.2f deg | %.1fx %.2f deg (%.0f %%) | %.1fx %.2f deg (%.0f %%)",
						turnNone, divLow, turnLow, 100.0 * turnLow / turnNone, divHigh, turnHigh, 100.0 * turnHigh / turnNone));
				checkNear(turnLow / turnNone, Math.pow(divLow, -0.35), 0.04, "2x zoom is only slightly slower (~78 %)");
				checkNear(turnHigh / turnNone, Math.pow(divHigh, -0.35), 0.04, "high zoom is clearly slower (~36 % at ~19x)");
				ctx.waitTicks(2);
				ctx.takeScreenshot("00_hand_back_after_zoom");
				log("   (hand visible again: see screenshot 00_hand_back_after_zoom)");
				checkNear(divisor(ctx), 1.0, 0.01, "FOV back to normal");
				checkNear(serverState(ctx, server, uuid), 0, 0, "server state back to 0");
				ctx.waitTicks(40);
				check(ctx.computeOnClient(client -> EyeAnimationManager.get(ownId) == null), "own eyes fully retracted and animation state released");

				ctx.getInput().scroll(-1.0);
				ctx.waitTick();
				check(ctx.computeOnClient(client -> client.player.getInventory().getSelectedSlot()) != 0, "mouse wheel changes hotbar again after zoom");

				// ---- 6. Own eyes in front third person (open area, nothing between camera and player) ----
				server.runCommand("tp " + name + " 40.5 -60 40.5 180 0");
				ctx.waitTicks(20);
				connection.getClientLevel().waitForChunksRender();
				setCamera(ctx, CameraType.THIRD_PERSON_FRONT);
				ctx.waitTicks(10);
				ctx.takeScreenshot("02_own_front_no_zoom");
				ctx.getInput().holdKey(ZoomController.ZOOM_KEY);
				ctx.waitTicks(40);
				check(eye(ctx, ownId) > 0.2, "own eyes extended while zooming in third person");
				ctx.takeScreenshot("03_own_front_zoom_2x");

				for (int i = 0; i < 4; i++) {
					ctx.getInput().scroll(1.0);
					ctx.waitTick();
				}

				ctx.waitTicks(40);
				ctx.takeScreenshot("04_own_front_zoom_4.9x");
				server.runCommand("tp " + name + " 40.5 -60 40.5 180 35");
				ctx.waitTicks(20);
				ctx.takeScreenshot("05_own_front_zoom_4.9x_looking_down");
				server.runCommand("tp " + name + " 40.5 -60 40.5 180 0");
				ctx.getInput().holdKey(options -> options.keyShift);
				ctx.waitTicks(20);
				ctx.takeScreenshot("06_own_front_zoom_4.9x_sneaking");
				ctx.getInput().releaseKey(options -> options.keyShift);
				ctx.waitTicks(10);

				// ---- 6b. Switch to cartoon style with K while zooming ----
				check(ctx.computeOnClient(client -> EyesConfig.get().eyeStyle) == EyeStyle.STALK, "default eye style is stalk");
				ctx.getInput().pressKey(ZoomController.STYLE_KEY);
				ctx.waitTicks(10);
				check(ctx.computeOnClient(client -> EyesConfig.get().eyeStyle) == EyeStyle.CARTOON, "K switches to cartoon style");
				checkNear(serverState(ctx, server, uuid), expectedNet(2.0 * Math.pow(1.25, 4)), 0, "style switch sends no extra state (display-only setting)");
				ctx.waitTicks(20);
				ctx.takeScreenshot("07_own_front_cartoon_4.9x");
				ctx.getInput().releaseKey(ZoomController.ZOOM_KEY);
				ctx.waitTicks(40);
				setCamera(ctx, CameraType.THIRD_PERSON_FRONT);
				ctx.getInput().holdKey(ZoomController.ZOOM_KEY);
				ctx.waitTicks(40);
				ctx.takeScreenshot("08_own_front_cartoon_2x");
				ctx.getInput().pressKey(ZoomController.STYLE_KEY);
				ctx.waitTicks(10);
				check(ctx.computeOnClient(client -> EyesConfig.get().eyeStyle) == EyeStyle.STALK, "K switches back to stalk style");
				ctx.getInput().releaseKey(ZoomController.ZOOM_KEY);
				ctx.waitTicks(40);
				check(ctx.computeOnClient(client -> EyeAnimationManager.get(ownId) == null), "own eyes retracted after third-person test");
				setCamera(ctx, CameraType.FIRST_PERSON);
				server.runCommand("tp " + name + " 0.5 -60 4.5 180 0");
				ctx.waitTicks(20);
				connection.getClientLevel().waitForChunksRender();
				ctx.waitTicks(10);

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
					check(changed > 0.0003 && changed < 0.15, "remote eyes visibly rendered at " + labels[i] + " (only a small region changes)");
				}

				server.runCommand("tp " + name + " 4.5 -60 0.5 90 0");
				ctx.waitTicks(10);
				Path stalkFront = ctx.takeScreenshot("15_mannequin_front_20x");

				// Our own setting decides how other players' eyes look: switch locally to cartoon.
				// The other player (mannequin) keeps sending exactly the same data.
				ctx.getInput().pressKey(ZoomController.STYLE_KEY);
				ctx.waitTicks(5);
				check(ctx.computeOnClient(client -> EyesConfig.get().eyeStyle) == EyeStyle.CARTOON, "local setting switched to cartoon");
				Path cartoonFront = ctx.takeScreenshot("16_mannequin_front_cartoon_20x");
				double styleDiff = changedPixels(stalkFront, cartoonFront);
				log(String.format("   stalk vs cartoon (same remote data): %.2f %% pixels differ", styleDiff * 100));
				check(styleDiff > 0.0003 && styleDiff < 0.15, "other player's eyes rendered in the viewer's own style (cartoon)");
				server.runCommand("tp " + name + " 0.5 -60 4.5 180 0");
				ctx.waitTicks(10);
				ctx.takeScreenshot("17_mannequin_side_cartoon_20x");
				sendToClient(server, mannequinId, EyeMath.toNet(0.54f));
				ctx.waitTicks(40);
				ctx.takeScreenshot("18_mannequin_side_cartoon_5x");
				ctx.getInput().pressKey(ZoomController.STYLE_KEY);
				ctx.waitTicks(5);
				check(ctx.computeOnClient(client -> EyesConfig.get().eyeStyle) == EyeStyle.STALK, "local setting back to stalk");

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

	/** Moves the mouse once; returns {total yaw change, change after the first tick, change after 0.5 s}. */
	private static float[] measureGlide(ClientGameTestContext ctx) {
		// Vanilla ignores the first mouse move after the cursor was (re)grabbed, e.g. after closing a menu.
		ctx.getInput().moveCursor(1.0, 0.0);
		ctx.waitTicks(30);
		float start = ctx.computeOnClient(client -> client.player.getYRot());
		ctx.getInput().moveCursor(300.0, 0.0);
		ctx.waitTick();
		float afterFirst = ctx.computeOnClient(client -> client.player.getYRot());
		ctx.waitTicks(9);
		float afterHalfSecond = ctx.computeOnClient(client -> client.player.getYRot());
		ctx.waitTicks(30);
		float end = ctx.computeOnClient(client -> client.player.getYRot());
		return new float[] {Math.abs(end - start), Math.abs(end - afterFirst), Math.abs(end - afterHalfSecond)};
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
