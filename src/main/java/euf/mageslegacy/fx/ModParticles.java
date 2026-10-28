package euf.mageslegacy.fx;
import euf.mageslegacy.MagesLegacy;
import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;
public final class ModParticles {
 public static final DeferredRegister<ParticleType<?>> REGISTER = DeferredRegister.create(Registries.PARTICLE_TYPE, MagesLegacy.MOD_ID);
 public static final Supplier<SimpleParticleType> SAND = REGISTER.register("sand", () -> new SimpleParticleType(false) {});
 public static final Supplier<SimpleParticleType> STILL_SAND = REGISTER.register("still_sand", () -> new SimpleParticleType(false) {});
 public static final Supplier<SimpleParticleType> ICE_SHARD = REGISTER.register("ice_shard", () -> new SimpleParticleType(false) {});
 public static final Supplier<SimpleParticleType> SPORE = REGISTER.register("spore", () -> new SimpleParticleType(false) {});
 public static final Supplier<SimpleParticleType> WISP = REGISTER.register("wisp", () -> new SimpleParticleType(false) {});
 public static final Supplier<SimpleParticleType> MOTE = REGISTER.register("mote", () -> new SimpleParticleType(false) {});
 public static final Supplier<SimpleParticleType> BLOOD = REGISTER.register("blood", () -> new SimpleParticleType(false) {});
 public static final Supplier<SimpleParticleType> BLOOD_MOTE = REGISTER.register("blood_mote", () -> new SimpleParticleType(false) {});
 public static final Supplier<SimpleParticleType> EMBER = REGISTER.register("ember", () -> new SimpleParticleType(false) {});
 public static final Supplier<SimpleParticleType> BLOOD_MIST = REGISTER.register("blood_mist", () -> new SimpleParticleType(false) {});
 public static final Supplier<SimpleParticleType> BAT = REGISTER.register("bat", () -> new SimpleParticleType(false) {});
 public static final Supplier<SimpleParticleType> VOID_MOTE = REGISTER.register("void_mote", () -> new SimpleParticleType(false) {});
 public static final Supplier<SimpleParticleType> STAR = REGISTER.register("star", () -> new SimpleParticleType(false) {});
}
