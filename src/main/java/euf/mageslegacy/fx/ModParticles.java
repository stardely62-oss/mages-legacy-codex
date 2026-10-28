package euf.mageslegacy.fx;

import euf.mageslegacy.MagesLegacy;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/** The races' own particles: grains of sand, ice shards, spores, spirit wisps. Sprites come from tools/gen_particles.py. */
public final class ModParticles {
	/** A grain of the sand of time: falls and drifts. */
	public static final SimpleParticleType SAND = FabricParticleTypes.simple();
	/** A grain that hangs where it is, glinting: time has stopped around it. */
	public static final SimpleParticleType STILL_SAND = FabricParticleTypes.simple();
	/** A shard of ice: tumbles as it falls. */
	public static final SimpleParticleType ICE_SHARD = FabricParticleTypes.simple();
	/** A glowing spore: floats up swaying, swells and bursts. */
	public static final SimpleParticleType SPORE = FabricParticleTypes.simple();
	/** A spirit wisp: rises flickering, glowing. */
	public static final SimpleParticleType WISP = FabricParticleTypes.simple();
	/** A golden mote of the Song of Bonds: drifts where it's sent, glowing warm. */
	public static final SimpleParticleType MOTE = FabricParticleTypes.simple();
	/** A drop of blood: falls and splashes into a stain on the ground. */
	public static final SimpleParticleType BLOOD = FabricParticleTypes.simple();
	/** A glowing drop of drawn blood: drifts where it's sent (the Harvest's streams). */
	public static final SimpleParticleType BLOOD_MOTE = FabricParticleTypes.simple();
	/** A crimson ember: rises off a feasting Bloodkin, swirling, glowing. */
	public static final SimpleParticleType EMBER = FabricParticleTypes.simple();
	/** Dark blood mist: billows out, swells and thins away. */
	public static final SimpleParticleType BLOOD_MIST = FabricParticleTypes.simple();
	/** A bat of the swarm: flies off along its velocity, beating its wings. */
	public static final SimpleParticleType BAT = FabricParticleTypes.simple();
	/** A mote of the void: a speck of dark with a violet rim, carried along its velocity, shrinking. */
	public static final SimpleParticleType VOID_MOTE = FabricParticleTypes.simple();
	/** A star: a tiny cross of light that twinkles where it hangs. */
	public static final SimpleParticleType STAR = FabricParticleTypes.simple();

	private ModParticles() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, MagesLegacy.id("sand"), SAND);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, MagesLegacy.id("still_sand"), STILL_SAND);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, MagesLegacy.id("ice_shard"), ICE_SHARD);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, MagesLegacy.id("spore"), SPORE);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, MagesLegacy.id("wisp"), WISP);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, MagesLegacy.id("mote"), MOTE);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, MagesLegacy.id("blood"), BLOOD);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, MagesLegacy.id("blood_mote"), BLOOD_MOTE);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, MagesLegacy.id("ember"), EMBER);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, MagesLegacy.id("blood_mist"), BLOOD_MIST);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, MagesLegacy.id("bat"), BAT);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, MagesLegacy.id("void_mote"), VOID_MOTE);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, MagesLegacy.id("star"), STAR);
	}
}
