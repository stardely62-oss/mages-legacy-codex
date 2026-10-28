package euf.mageslegacy.mixin.client;

import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The Voidborn's pocket is a dimension from the mod's own data, and vanilla calls any world with such a dimension
 * "experimental", asking about it on every load. Here that only means the pocket exists: the question is skipped.
 * Lenient (require 0): if the method ever changes, the old prompt simply comes back.
 */
@Mixin(WorldOpenFlows.class)
public class IntegratedServerLoaderMixin {
	/** Opening a world: no "experimental settings" prompt. */
	@ModifyVariable(method = "start(Lnet/minecraft/client/gui/screen/Screen;Ljava/lang/String;ZZ)V", at = @At("HEAD"), argsOnly = true, ordinal = 1,
			require = 0)
	private boolean mages_legacy$noExperimentalPrompt(boolean canShowBackupPrompt) {
		return false;
	}

	/** Creating a world: the same warning, bypassed. */
	@ModifyVariable(method = "tryLoad", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private static boolean mages_legacy$bypassWarnings(boolean bypassWarnings) {
		return true;
	}
}
