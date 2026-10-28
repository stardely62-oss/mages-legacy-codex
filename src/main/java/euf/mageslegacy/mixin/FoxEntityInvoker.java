package euf.mageslegacy.mixin;

import java.util.UUID;
import net.minecraft.world.entity.animal.Fox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A bonded fox trusts its Shepherd, so it no longer runs away. */
@Mixin(Fox.class)
public interface FoxEntityInvoker {
	@Invoker("addTrustedUUID")
	void mages_legacy$trust(UUID uuid);
}
