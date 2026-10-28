package euf.mageslegacy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import euf.mageslegacy.masked.Face;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Which players wear which face, as the server told it, and the stand-in bodies they are drawn as: a monster of the
 * face's kind, never added to the world, that copies the wearer's place, turn and stride every frame.
 */
public final class FaceVisuals {
	private static final Int2ObjectMap<Face> FACES = new Int2ObjectOpenHashMap<>();
	private static final Int2ObjectMap<LivingEntity> BODIES = new Int2ObjectOpenHashMap<>();

	private FaceVisuals() {
	}

	public static void read(FriendlyByteBuf buf) {
		FACES.clear();
		int count = buf.readVarInt();
		Face[] faces = Face.values();
		for (int i = 0; i < count; i++) {
			int id = buf.readVarInt();
			int face = buf.readVarInt();
			if (face >= 0 && face < faces.length) {
				FACES.put(id, faces[face]);
			}
		}
		// A body whose wearer changed faces (or took it off) is made anew
		BODIES.int2ObjectEntrySet().removeIf(e -> FACES.get(e.getIntKey()) == null || !FACES.get(e.getIntKey()).look().equals(e.getValue().getType()));
	}

	/** The face this entity is drawn in, or null. */
	public static Face of(int entityId) {
		return FACES.get(entityId);
	}

	public static void clear() {
		FACES.clear();
		BODIES.clear();
	}

	/** Walks the stand-ins in step with their wearers, once a client tick. */
	public static void tick(Minecraft client) {
		if (client.level == null) {
			return;
		}
		for (Int2ObjectMap.Entry<LivingEntity> e : BODIES.int2ObjectEntrySet()) {
			if (client.level.getEntity(e.getIntKey()) instanceof Player player) {
				LivingEntity body = e.getValue();
				body.walkAnimation.update(player.walkAnimation.speed(), 1.0f);
				body.tickCount = player.tickCount;
			}
		}
	}

	/** Draws a wearer as its face's monster; false when it wears none and should be drawn as itself. */
	public static boolean render(Player player, float yaw, float tickDelta, PoseStack matrices, MultiBufferSource vertices, int light) {
		Face face = FACES.get(player.getId());
		if (face == null || player.isInvisible()) {
			return false;
		}
		LivingEntity body = BODIES.get(player.getId());
		if (body == null) {
			Entity made = face.look().create(player.level());
			if (!(made instanceof LivingEntity living)) {
				return false;
			}
			body = living;
			BODIES.put(player.getId(), body);
		}
		body.copyPosition(player);
		body.xo = player.xo;
		body.yo = player.yo;
		body.zo = player.zo;
		body.yRotO = player.yRotO;
		body.xRotO = player.xRotO;
		body.yBodyRot = player.yBodyRot;
		body.yBodyRotO = player.yBodyRotO;
		body.yHeadRot = player.yHeadRot;
		body.yHeadRotO = player.yHeadRotO;
		body.attackAnim = player.attackAnim;
		body.oAttackAnim = player.oAttackAnim;
		body.hurtTime = player.hurtTime;
		body.setOnGround(player.onGround());
		body.setShiftKeyDown(player.isShiftKeyDown());
		// Other players see whose face it is; the wearer's own third-person view stays clean
		boolean named = player != Minecraft.getInstance().getCameraEntity();
		body.setCustomName(named ? player.getDisplayName() : null);
		body.setCustomNameVisible(named);
		EntityRenderer<? super LivingEntity> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(body);
		renderer.render(body, yaw, tickDelta, matrices, vertices, light);
		return true;
	}
}
