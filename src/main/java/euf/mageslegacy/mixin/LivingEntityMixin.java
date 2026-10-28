package euf.mageslegacy.mixin;

import euf.mageslegacy.bloodkin.Bloodkin;
import euf.mageslegacy.frozen.Frozen;
import euf.mageslegacy.mercenary.Mercenary;
import euf.mageslegacy.mycelid.Mycelid;
import euf.mageslegacy.race.ClientRaceView;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.shepherd.Shepherd;
import euf.mageslegacy.masked.Masked;
import euf.mageslegacy.tempter.Tempter;
import euf.mageslegacy.voidborn.Voidborn;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Race rules that change damage: Mycelids burn half again as badly; Shepherds hit softer, their creatures don't hurt
 * each other or their Shepherd, and the Voice and Pack talents shift who takes what; blows pass through the Frozen
 * mid-Rewind or by Blur. Also: good effects run shorter on the Frozen.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float mages_legacy$raceDamage(float amount, DamageSource source) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.level().isClientSide()) {
			return amount;
		}
		Race race = self instanceof ServerPlayer player ? RaceState.raceOf(player).orElse(null) : null;
		if (race == Race.MYCELID) {
			amount = Mycelid.modifyDamage((ServerPlayer) self, source, amount);
		} else if (race == Race.FROZEN) {
			amount = Frozen.modifyDamage((ServerPlayer) self, source, amount);
		} else if (race == Race.MERCENARY) {
			amount = Mercenary.modifyDamage((ServerPlayer) self, source, amount);
		} else if (race == Race.MASKED) {
			amount = Masked.modifyDamage((ServerPlayer) self, source, amount);
		}
		amount = Progression.scale(source, amount);
		Voidborn.onHurt(self, amount);
		amount = Frozen.onHit(self, source, amount);
		amount = Bloodkin.onHit(self, source, amount);
		amount = Mercenary.onHit(self, source, amount);
		amount = Tempter.onHit(self, source, amount);
		amount = Masked.onHit(self, source, amount);
		amount = Shepherd.modifyDamage(self, source, amount);
		return race == Race.TEMPTER ? Tempter.modifyDamage((ServerPlayer) self, source, amount) : amount;
	}

	/** A Masked in a spider's face climbs walls like a ladder; the client decides for its own player, the server for all. */
	@Inject(method = "isClimbing", at = @At("RETURN"), cancellable = true)
	private void mages_legacy$spiderFace(CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (cir.getReturnValueZ() || !(self instanceof Player player) || !player.horizontalCollision || player.isSpectator()) {
			return;
		}
		if (self.level().isClientSide() ? player.isLocalPlayer() && ClientRaceView.climbs : Masked.climbs(player)) {
			cir.setReturnValue(true);
		}
	}

	/** No friendly fire, no blow on a rewinding or blurred Frozen: it doesn't land at all (no knockback, no flash). */
	@Inject(method = "damage", at = @At("HEAD"), cancellable = true)
	private void mages_legacy$friendlyFire(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.level().isClientSide() && (Shepherd.friendlyFire(self, source) || Frozen.avoids(self, source) || Bloodkin.avoids(self)
				|| Voidborn.avoids(self, source))) {
			cir.setReturnValue(false);
		}
	}

	@ModifyVariable(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z",
			at = @At("HEAD"), argsOnly = true)
	private MobEffectInstance mages_legacy$shorterEffects(MobEffectInstance effect) {
		LivingEntity self = (LivingEntity) (Object) this;
		return self.level().isClientSide() ? effect : Frozen.shorten(self, effect);
	}
}
