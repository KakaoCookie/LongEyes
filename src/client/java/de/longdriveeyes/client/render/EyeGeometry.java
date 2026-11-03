package de.longdriveeyes.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Emits the eye geometry (stalk, rounded eyeball, pupil, highlight) as plain boxes.
 *
 * <p>Local space: origin at the anchor inside the face, the eye looks along -Z, +Y is down.
 * Colours come from the vertex colour on a white texture.
 */
public final class EyeGeometry {
	private static final int STALK_COLOR = 0xFFD98C8C;
	private static final int STALK_RING_COLOR = 0xFFC27373;
	private static final int EYE_COLOR = 0xFFF7F7F2;
	private static final int PUPIL_COLOR = 0xFF111111;
	private static final int HIGHLIGHT_COLOR = 0xFFFFFFFF;

	/** Inner box ratio of the chamfered ("rounded voxel") eyeball. */
	private static final float CHAMFER = 0.78f;

	private EyeGeometry() {
	}

	public static void emitEye(PoseStack.Pose pose, VertexConsumer consumer, float length, float size, float stalk, int light) {
		float half = size * 0.5f;
		// At length == 0 the eyeball sits completely inside the head; it pops out as the stalk grows.
		float centerZ = -length + half;

		// --- Stalk: from the anchor to the eyeball centre ---
		if (centerZ < 0.0f) {
			float t = stalk * 0.5f;
			box(pose, consumer, -t, -t, centerZ, t, t, 0.0f, STALK_COLOR, light);

			// Telescope rings make the extension visible like segments sliding out.
			float ring = t * 1.45f;
			float ringDepth = Math.min(stalk * 0.6f, 0.03f);
			int segments = Math.min(6, (int) (-centerZ / 0.3f));

			for (int i = 1; i <= segments; i++) {
				float z = centerZ * i / (segments + 1.0f);
				box(pose, consumer, -ring, -ring, z - ringDepth, ring, ring, z + ringDepth, STALK_RING_COLOR, light);
			}
		}

		// --- Eyeball: three overlapping boxes give a rounded, slightly chamfered voxel look ---
		float inner = half * CHAMFER;
		box(pose, consumer, -half, -inner, centerZ - inner, half, inner, centerZ + inner, EYE_COLOR, light);
		box(pose, consumer, -inner, -half, centerZ - inner, inner, half, centerZ + inner, EYE_COLOR, light);
		box(pose, consumer, -inner, -inner, centerZ - half, inner, inner, centerZ + half, EYE_COLOR, light);

		// --- Pupil on the front (-Z) face, looking where the player looks ---
		float pupil = size * 0.22f;
		float pupilDepth = size * 0.06f;
		float front = centerZ - half;
		box(pose, consumer, -pupil, -pupil, front - pupilDepth, pupil, pupil, front + pupilDepth, PUPIL_COLOR, light);

		// --- Tiny highlight for the cartoon look ---
		float hl = pupil * 0.35f;
		float hx = -pupil * 0.45f;
		float hy = -pupil * 0.45f;
		box(pose, consumer, hx - hl, hy - hl, front - pupilDepth - 0.002f, hx + hl, hy + hl, front - pupilDepth + 0.001f, HIGHLIGHT_COLOR, light);
	}

	/** Axis aligned box, counter-clockwise winding seen from outside on every face. */
	private static void box(PoseStack.Pose pose, VertexConsumer c, float x0, float y0, float z0, float x1, float y1, float z1, int color, int light) {
		Matrix4f m = pose.pose();
		// +X
		quad(m, pose, c, color, light, 1, 0, 0, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
		// -X
		quad(m, pose, c, color, light, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		// +Y
		quad(m, pose, c, color, light, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
		// -Y
		quad(m, pose, c, color, light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		// +Z
		quad(m, pose, c, color, light, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		// -Z
		quad(m, pose, c, color, light, 0, 0, -1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
	}

	private static void quad(Matrix4f m, PoseStack.Pose pose, VertexConsumer c, int color, int light, float nx, float ny, float nz,
			float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
		vertex(m, pose, c, color, light, nx, ny, nz, ax, ay, az, 0.0f, 0.0f);
		vertex(m, pose, c, color, light, nx, ny, nz, bx, by, bz, 0.0f, 1.0f);
		vertex(m, pose, c, color, light, nx, ny, nz, cx, cy, cz, 1.0f, 1.0f);
		vertex(m, pose, c, color, light, nx, ny, nz, dx, dy, dz, 1.0f, 0.0f);
	}

	private static void vertex(Matrix4f m, PoseStack.Pose pose, VertexConsumer c, int color, int light,
			float nx, float ny, float nz, float x, float y, float z, float u, float v) {
		c.addVertex(m, x, y, z)
				.setColor(color)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, nx, ny, nz);
	}
}
