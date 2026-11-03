package de.longdriveeyes.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import de.longdriveeyes.LongDriveEyes;
import de.longdriveeyes.config.EyesConfig;

/**
 * Player render layer that draws the telescoping eyes in front of the head.
 *
 * <p>The geometry is attached to the head {@code ModelPart} of the vanilla player model, so it follows
 * head yaw/pitch and every body pose (walking, sprinting, sneaking, swimming, elytra, sleeping ...)
 * automatically. It is submitted through the regular entity submit pipeline with a vanilla entity
 * render type, so shader packs (Iris) and Sodium treat it like any other entity geometry.
 */
public final class EyeStalkLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = LongDriveEyes.id("textures/entity/eye_white.png");
	private static final RenderType RENDER_TYPE = RenderTypes.entitySolid(TEXTURE);

	// Head model coordinates (1/16 block units, +Y down, -Z = face). Typical skin eye row and columns.
	private static final float EYE_X = 2.0f / 16.0f;
	private static final float EYE_Y = -3.5f / 16.0f;
	/** Anchor half a pixel behind the face so the stalk visibly grows out of the head. */
	private static final float ANCHOR_Z = -3.5f / 16.0f;
	private static final float ANCHOR_DEPTH = 0.5f / 16.0f;

	public EyeStalkLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector nodeCollector, int light, AvatarRenderState state, float yRot, float xRot) {
		EyeRenderData data = state.getData(EyeRenderData.KEY);

		if (data == null || data.extension <= 0.004f) {
			return;
		}

		EyesConfig config = EyesConfig.get();
		float length = data.extension * (float) config.maxEyeExtension + ANCHOR_DEPTH;
		float eyeSize = (float) config.eyeSize;
		float stalk = (float) config.eyeStalkThickness;
		float extension = Math.min(data.extension, 1.0f);

		poseStack.pushPose();
		this.getParentModel().getHead().translateAndRotate(poseStack);

		for (int side = -1; side <= 1; side += 2) {
			float yaw = -side * 5.0f * extension; // splay slightly outward, goofy look
			float pitch = 0.0f;

			if (config.eyeWobble) {
				float phase = data.time * 0.21f + (side > 0 ? 0.0f : 1.7f);
				yaw += (float) Math.sin(phase) * 3.0f * extension;
				// Long stalks sag a bit and whip back with the spring velocity.
				pitch += 6.0f * extension + (float) Math.sin(phase * 1.3f) * 2.0f * extension - data.velocity * 60.0f;
			}

			poseStack.pushPose();
			poseStack.translate(side * EYE_X, EYE_Y, ANCHOR_Z);
			poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
			poseStack.mulPose(Axis.XP.rotationDegrees(pitch));

			final float lengthFinal = length;
			nodeCollector.submitCustomGeometry(poseStack, RENDER_TYPE,
					(pose, consumer) -> EyeGeometry.emitEye(pose, consumer, lengthFinal, eyeSize, stalk, light));
			poseStack.popPose();
		}

		poseStack.popPose();
	}
}
