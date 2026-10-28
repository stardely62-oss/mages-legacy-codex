package euf.mageslegacy.voidborn;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

/**
 * The pocket dimension: a starry nothing where every Voidborn has a room of their own, a floating island they step
 * into and back out of to the very spot they left. Rooms lie in a row along x, far apart. Each also has a sealed
 * cell high above it, where creatures pulled out of the world by the Excision wait.
 */
public final class Pocket {
	public static final ResourceKey<Level> WORLD = ResourceKey.create(Registries.DIMENSION, MagesLegacy.id("pocket"));
	/** Blocks between two rooms. */
	public static final int SPACING = 512;
	/** The floor's height, and the half-size of the island: 9x9 inside a ring, 15x15 with Wider Pocket. */
	public static final int FLOOR = 64;
	public static final int HALF = 4;
	public static final int HALF_WIDE = 7;
	/** The cell's floor, far above the island. */
	private static final int CELL = 200;

	private Pocket() {
	}

	public static ServerLevel world(MinecraftServer server) {
		return server.getLevel(WORLD);
	}

	public static boolean in(Entity entity) {
		return entity.level().dimension() == WORLD;
	}

	/** The middle of a room's floor, where one steps in. */
	public static Vec3 center(int room) {
		return new Vec3(room * SPACING + 0.5, FLOOR + 1, 0.5);
	}

	/** Inside the sealed cell above a room. */
	public static Vec3 cell(int room) {
		return new Vec3(room * SPACING + 0.5, CELL + 1, 0.5);
	}

	/** How far this Voidborn's island reaches from its middle. */
	public static int half(RaceData data) {
		return data.has(Talents.POCKET_WIDE) ? HALF_WIDE : HALF;
	}

	/** This player's room, given one on the first visit. */
	public static int room(ServerPlayer player, RaceData data) {
		if (data.voidborn.room < 0) {
			data.voidborn.room = RaceState.get(player.getServer()).nextPocketRoom();
			RaceState.get(player.getServer()).setDirty();
		}
		return data.voidborn.room;
	}

	/**
	 * The island, laid once and widened later if it must: a floor of purpur ringed with obsidian, crying obsidian at
	 * the corners with end rods for light, amethyst in the middle. Widening takes away only the old ring and its rods
	 * (whatever was built on the island stays). Above it, out of sight, a cell of barrier blocks.
	 */
	public static void build(ServerLevel world, int room, VoidData data, int half) {
		BlockPos c = BlockPos.containing(center(room)).below();
		world.getChunk(c);
		if (data.builtHalf < half) {
			int old = data.builtHalf;
			if (old > 0) {
				for (int dx = -old - 1; dx <= old + 1; dx++) {
					for (int dz = -old - 1; dz <= old + 1; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) == old + 1) {
							world.setBlockAndUpdate(c.offset(dx, 0, dz), Blocks.PURPUR_BLOCK.defaultBlockState());
							if (world.getBlockState(c.offset(dx, 1, dz)).is(Blocks.END_ROD)) {
								world.removeBlock(c.offset(dx, 1, dz), false);
							}
						}
					}
				}
			}
			for (int dx = -half - 1; dx <= half + 1; dx++) {
				for (int dz = -half - 1; dz <= half + 1; dz++) {
					int ring = Math.max(Math.abs(dx), Math.abs(dz));
					if (old > 0 && ring <= old + 1) {
						continue;
					}
					boolean edge = ring == half + 1;
					boolean corner = Math.abs(dx) == half + 1 && Math.abs(dz) == half + 1;
					BlockState state = corner ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : edge ? Blocks.OBSIDIAN.defaultBlockState()
							: ring <= 1 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.PURPUR_BLOCK.defaultBlockState();
					world.setBlockAndUpdate(c.offset(dx, 0, dz), state);
					if (corner) {
						world.setBlockAndUpdate(c.offset(dx, 1, dz), Blocks.END_ROD.defaultBlockState());
					}
				}
			}
			if (old == 0) {
				world.setBlockAndUpdate(c, Blocks.AMETHYST_BLOCK.defaultBlockState());
			}
			data.builtHalf = half;
		}
		BlockPos cell = BlockPos.containing(cell(room)).below();
		world.getChunk(cell);
		if (!world.getBlockState(cell).is(Blocks.BARRIER)) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dy = 0; dy <= 4; dy++) {
					for (int dz = -2; dz <= 2; dz++) {
						boolean shell = Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == 0 || dy == 4;
						world.setBlockAndUpdate(cell.offset(dx, dy, dz), shell ? Blocks.BARRIER.defaultBlockState() : Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
	}

	/** Into one's own room, remembering the way back; with Guests, the players standing close come along. */
	public static void enter(ServerPlayer player, RaceData data) {
		ServerLevel pocket = world(player.getServer());
		if (pocket == null) {
			return;
		}
		int room = room(player, data);
		build(pocket, room, data.voidborn, half(data));
		List<ServerPlayer> guests = !data.has(Talents.POCKET_GUESTS) ? List.of()
				: player.serverLevel().getPlayers(p -> p != player && p.isAlive() && !p.isSpectator() && p.distanceToSqr(player) < 9);
		data.voidborn.returnWorld = player.level().dimension();
		data.voidborn.returnPos = player.position();
		data.voidborn.returnYaw = player.getYRot();
		data.voidborn.returnPitch = player.getXRot();
		data.dirty = true;
		RaceState.get(player.getServer()).setDirty();
		Vec3 to = center(room);
		player.teleportTo(pocket, to.x, to.y, to.z, player.getYRot(), 0);
		player.fallDistance = 0;
		for (int i = 0; i < guests.size(); i++) {
			double a = Math.PI * 2 * i / Math.max(1, guests.size());
			guests.get(i).teleportTo(pocket, to.x + Math.cos(a) * 2, to.y, to.z + Math.sin(a) * 2, guests.get(i).getYRot(), 0);
		}
	}

	/**
	 * Back out to where one stepped in (or the world's spawn if that world is gone); {@code home} (Way Home) goes to
	 * one's own spawn point instead. Guests in the room come out with their host.
	 */
	public static void leave(ServerPlayer player, RaceData data, boolean home) {
		MinecraftServer server = player.getServer();
		VoidData v = data.voidborn;
		List<ServerPlayer> guests = player.serverLevel().getPlayers(p -> p != player && inRoom(p, v.room));
		ServerLevel to = v.returnWorld == null ? null : server.getLevel(v.returnWorld);
		if (home && server.getLevel(player.getRespawnDimension()) != null && player.getRespawnPosition() != null) {
			ServerLevel spawnWorld = server.getLevel(player.getRespawnDimension());
			BlockPos spawn = player.getRespawnPosition();
			Vec3 at = player.findRespawnPositionAndUseSpawnBlock(false, net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING).pos();
			player.teleportTo(spawnWorld, at.x, at.y, at.z, player.getYRot(), player.getXRot());
		} else if (to == null || v.returnPos == null) {
			to = server.overworld();
			BlockPos spawn = to.getSharedSpawnPos();
			player.teleportTo(to, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), player.getXRot());
		} else {
			player.teleportTo(to, v.returnPos.x, v.returnPos.y, v.returnPos.z, v.returnYaw, v.returnPitch);
		}
		player.fallDistance = 0;
		for (ServerPlayer guest : guests) {
			guest.teleportTo(player.serverLevel(), player.getX(), player.getY(), player.getZ(), guest.getYRot(), guest.getXRot());
			guest.fallDistance = 0;
		}
		v.returnWorld = null;
		v.returnPos = null;
		data.dirty = true;
		RaceState.get(server).setDirty();
	}

	/** Whether this player stands in their own room (and not someone else's, or the cell). */
	public static boolean inOwnRoom(ServerPlayer player, RaceData data) {
		return inRoom(player, data.voidborn.room);
	}

	/** Whether an entity is in the pocket, within a given room's stretch of it. */
	public static boolean inRoom(Entity entity, int room) {
		return in(entity) && room >= 0 && Math.abs(entity.getX() - center(room).x) < SPACING / 2.0;
	}

	/** Moves any entity (a player too) to another world and spot; returns the entity as it is there. */
	public static Entity move(Entity entity, ServerLevel to, Vec3 pos, float yaw) {
		to.getChunk(BlockPos.containing(pos));
		if (entity instanceof ServerPlayer player) {
			player.teleportTo(to, pos.x, pos.y, pos.z, yaw, player.getXRot());
			return player;
		}
		if (entity.level() == to) {
			entity.moveTo(pos.x, pos.y, pos.z, yaw, entity.getXRot());
			entity.setDeltaMovement(Vec3.ZERO);
			return entity;
		}
		Entity moved = entity.changeDimension(new DimensionTransition(to, pos, Vec3.ZERO, yaw, entity.getXRot(), DimensionTransition.DO_NOTHING));
		return moved == null ? entity : moved;
	}

	/** Falling off the island is no death: whoever drops below it comes back up onto its middle. */
	public static void catchFallers(MinecraftServer server) {
		ServerLevel pocket = world(server);
		if (pocket == null) {
			return;
		}
		for (ServerPlayer player : pocket.players()) {
			if (player.getY() < FLOOR - 24) {
				int room = (int) Math.floor((player.getX() + SPACING / 2.0) / SPACING);
				Vec3 to = center(Math.max(0, room));
				player.teleportTo(pocket, to.x, to.y, to.z, player.getYRot(), player.getXRot());
				player.setDeltaMovement(Vec3.ZERO);
				player.fallDistance = 0;
			}
		}
	}
}
