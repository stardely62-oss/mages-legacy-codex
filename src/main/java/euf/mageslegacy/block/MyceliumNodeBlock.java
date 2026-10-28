package euf.mageslegacy.block;

import euf.mageslegacy.fx.ModParticles;
import euf.mageslegacy.mycelid.Mycelid;
import euf.mageslegacy.race.RaceState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A glowing mushroom a Mycelid grows; right click carries anyone to the next node of the same network. */
public class MyceliumNodeBlock extends Block {
	private static final VoxelShape SHAPE = Shapes.or(
			Block.box(6, 0, 6, 10, 7, 10),
			Block.box(2, 7, 2, 14, 13, 14));

	public MyceliumNodeBlock(Properties settings) {
		super(settings);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
		if (world.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		return Mycelid.travel((ServerPlayer) player, pos) ? InteractionResult.CONSUME : InteractionResult.PASS;
	}

	@Override
	public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.is(newState.getBlock()) && world instanceof ServerLevel serverWorld) {
			RaceState.get(serverWorld.getServer()).removeNode(world.dimension(), pos);
		}
		super.onRemove(state, world, pos, newState, moved);
	}

	/** A living node breathes: glowing spores keep drifting up from it, now and then a blossom's speck. */
	@Override
	public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
		for (int i = 0; i < 4; i++) {
			world.addParticle(ModParticles.SPORE.get(), pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8, pos.getY() + 0.4 + random.nextDouble() * 0.6,
					pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8, 0, 0.02, 0);
		}
		if (random.nextInt(3) == 0) {
			world.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, pos.getX() + random.nextDouble(), pos.getY() + 0.8,
					pos.getZ() + random.nextDouble(), 0, 0.01, 0);
		}
	}
}
