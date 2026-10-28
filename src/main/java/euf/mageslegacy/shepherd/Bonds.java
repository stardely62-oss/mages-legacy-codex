package euf.mageslegacy.shepherd;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.fx.ModParticles;
import euf.mageslegacy.mixin.FoxEntityInvoker;
import euf.mageslegacy.race.AbilityResult;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import euf.mageslegacy.shepherd.ShepherdData.Bond;
import euf.mageslegacy.shepherd.ShepherdData.Ritual;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Bonds with living beasts. The ritual: sing the Song of Bonds to a beast (its own ability key), then win it over
 * its own way while it listens. A bonded beast follows its Shepherd, grows in levels and brings its skill; it can be
 * petted, fed and told to stay; one that falls rests for a while and comes back.
 */
public final class Bonds {
	public static final int SONG_COST = 20;
	public static final int SONG_TICKS = 24000;
	public static final int SONG_SLOT = 3;
	public static final int SONG_COOLDOWN = 600;
	public static final int FEED_GAP = 1200;
	public static final int PET_GAP = 2400;
	public static final int NOT_TODAY_GAP = 6000;
	private static final int TURTLE_SLOT = 6;
	private static final net.minecraft.resources.ResourceLocation LEVEL_HEALTH = euf.mageslegacy.MagesLegacy.id("attribute/bonds/level_health");
	private static final net.minecraft.resources.ResourceLocation THICK_FUR = euf.mageslegacy.MagesLegacy.id("attribute/bonds/thick_fur");
	private static final net.minecraft.resources.ResourceLocation ALPHA_HEALTH = euf.mageslegacy.MagesLegacy.id("attribute/bonds/alpha_health");
	private static final net.minecraft.resources.ResourceLocation ALPHA_DAMAGE = euf.mageslegacy.MagesLegacy.id("attribute/bonds/alpha_damage");

	private Bonds() {
	}

	// ---- the ritual ----

	/**
	 * Right click on a beast. Your own: petting (empty hand), feeding (its treat), sit or follow (sneaking, empty
	 * hand). One listening to your Song: the beast's own way goes on (see {@link BeastKind.Taming}).
	 */
	public static InteractionResult interact(ServerPlayer player, RaceData data, Entity entity, InteractionHand hand) {
		Optional<BeastKind> found = BeastKind.of(entity.getType());
		if (found.isEmpty() || hand != InteractionHand.MAIN_HAND || !(entity instanceof Mob mob) || !mob.isAlive()) {
			return InteractionResult.PASS;
		}
		BeastKind kind = found.get();
		ShepherdData shepherd = data.shepherd;
		ItemStack stack = player.getMainHandItem();
		long now = player.serverLevel().getGameTime();
		Optional<Bond> own = shepherd.bond(entity.getUUID());
		if (own.isPresent()) {
			return care(player, data, mob, kind, own.get(), stack, now);
		}
		if (Creatures.ownerOf(entity) != null) {
			return InteractionResult.PASS;
		}
		Ritual ritual = shepherd.rituals.get(entity.getUUID());
		if (ritual == null || ritual.listenUntil < now || !kind.food().test(stack)) {
			return InteractionResult.PASS;
		}
		return switch (kind.taming()) {
			case FEED_BY_FIRE, FEED -> feed(player, data, mob, kind, ritual, stack, now);
			case WATER_GIFT -> player.isInWater() ? feed(player, data, mob, kind, ritual, stack, now) : hint(player, "mages_legacy.bond.swim_with_it");
			case ASLEEP -> gift(player, data, mob, kind, stack);
			case KINDS -> flower(player, data, mob, kind, ritual, stack);
			case HEADBUTTS -> ritual.feedings >= needed(kind) ? gift(player, data, mob, kind, stack) : hint(player, "mages_legacy.bond.headbutt_first");
			default -> InteractionResult.PASS;
		};
	}

	/** How many steps this beast's way takes: feedings, songs, headbutts, kills, or seconds of patience. */
	public static int needed(BeastKind kind) {
		return kind.steps();
	}

	private static InteractionResult hint(ServerPlayer player, String key, Object... args) {
		player.displayClientMessage(Component.translatable(key, args), true);
		return InteractionResult.FAIL;
	}

	/**
	 * The Song of Bonds (its own ability): sung to the beast you look at, within 8 blocks. It opens the ritual: for 20
	 * minutes the beast listens, and its own way can be walked. The parrot and the bat are won by songs alone.
	 */
	public static AbilityResult song(ServerPlayer player, RaceData data) {
		if (!Shepherd.ready(player, data, SONG_SLOT)) {
			return AbilityResult.COOLDOWN;
		}
		LivingEntity target = Shepherd.lookedAt(player, 8, e -> BeastKind.of(e.getType()).isPresent());
		if (target == null) {
			target = nearestToLook(player, 8, 12);
		}
		return target instanceof Mob mob ? singTo(player, data, mob) : AbilityResult.NO_BEAST;
	}

	/** A small beast is hard to hit with a straight look: the one closest to where you look, within {@code degrees}. */
	@Nullable
	private static LivingEntity nearestToLook(ServerPlayer player, double range, double degrees) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);
		double best = Math.cos(Math.toRadians(degrees));
		LivingEntity found = null;
		for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
				e -> e.isAlive() && BeastKind.of(e.getType()).isPresent() && e.distanceToSqr(player) < range * range)) {
			double cos = e.getBoundingBox().getCenter().subtract(eye).normalize().dot(look);
			if (cos > best && player.hasLineOfSight(e)) {
				best = cos;
				found = e;
			}
		}
		return found;
	}

	/** The Song to this beast, wherever the Shepherd looks (the cooldown is the caller's business). */
	public static AbilityResult singTo(ServerPlayer player, RaceData data, Mob mob) {
		if (data.shepherd.bond(mob.getUUID()).isPresent()) {
			return hintAbility(player, "mages_legacy.bond.already_yours", mob.getDisplayName());
		}
		if (Creatures.ownerOf(mob) != null) {
			return AbilityResult.NO_BEAST;
		}
		return sing(player, data, mob, BeastKind.of(mob.getType()).orElseThrow(), player.serverLevel().getGameTime());
	}

	private static AbilityResult hintAbility(ServerPlayer player, String key, Object... args) {
		player.displayClientMessage(Component.translatable(key, args), true);
		return AbilityResult.HINTED;
	}

	private static AbilityResult sing(ServerPlayer player, RaceData data, Mob mob, BeastKind kind, long now) {
		Ritual existing = data.shepherd.rituals.get(mob.getUUID());
		if (kind.taming() == BeastKind.Taming.ASLEEP && !(mob instanceof Fox fox && fox.isSleeping())) {
			return hintAbility(player, "mages_legacy.bond.fox_awake");
		}
		boolean songs = kind.taming() == BeastKind.Taming.SONGS;
		// The bat listens only underground, the parrot only to a song with music playing
		if (kind.id().equals("bat") && player.serverLevel().canSeeSky(mob.blockPosition())) {
			return hintAbility(player, "mages_legacy.bond.underground");
		}
		if (kind.id().equals("parrot") && !nearJukebox(player.serverLevel(), mob.blockPosition())) {
			return hintAbility(player, "mages_legacy.bond.need_music");
		}
		if (songs && existing != null && existing.feedings > 0 && now - existing.lastFeed < FEED_GAP) {
			return hintAbility(player, "mages_legacy.bond.too_soon", (FEED_GAP - (now - existing.lastFeed) + 19) / 20);
		}
		if (data.resource < SONG_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		data.addResource(-SONG_COST);
		Shepherd.cooldown(player, data, SONG_SLOT, SONG_COOLDOWN);
		boolean fresh = existing == null || existing.listenUntil < now;
		Ritual ritual = data.shepherd.rituals.computeIfAbsent(mob.getUUID(), id -> new Ritual());
		ritual.listenUntil = now + SONG_TICKS;
		ritual.kind = kind.id();
		data.dirty = true;
		ServerLevel world = player.serverLevel();
		// The voice made visible: a stream of golden motes flows from the Shepherd to the beast and settles around it
		Vec3 from = player.getEyePosition().subtract(0, 0.3, 0);
		Vec3 to = mob.position().add(0, mob.getBbHeight() * 0.6, 0);
		Vec3 way = to.subtract(from);
		for (int i = 0; i < 18; i++) {
			Vec3 at = from.add(way.scale(i / 18.0 * 0.4));
			Vec3 push = way.scale(0.06 + world.getRandom().nextDouble() * 0.015);
			world.sendParticles(ModParticles.MOTE.get(), at.x, at.y, at.z, 0, push.x, push.y, push.z, 1);
		}
		for (int i = 0; i < 10; i++) {
			double a = Math.PI * 2 * i / 10;
			double r = Math.max(0.6, mob.getBbWidth());
			world.sendParticles(ModParticles.MOTE.get(), to.x + Math.cos(a) * r, to.y, to.z + Math.sin(a) * r, 0, -Math.sin(a) * 0.06, 0.02,
					Math.cos(a) * 0.06, 1);
		}
		world.playSound(null, mob.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 1.0f, 0.8f);
		world.playSound(null, mob.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 1.0f, 1.2f);
		Fx.ring(world, mob.position(), 0.2f, 2.5f, 16, Shepherd.GOLD_RGB);
		Fx.mark(world, mob.position(), mob, Math.max(1.0f, mob.getBbWidth() + 0.6f), 30, Shepherd.GOLD_RGB, Fx.RIPPLES);
		// Only a new listener teaches the Shepherd anything: singing to the same beast again is no practice
		if (fresh) {
			Progression.addXp(player, data, "shepherd_song", 2, 20);
		}
		if (songs) {
			// The parrot and the bat are won by the songs themselves
			ritual.feedings++;
			ritual.lastFeed = now;
			if (ritual.feedings >= needed(kind)) {
				complete(player, data, mob, kind);
				return AbilityResult.OK;
			}
			player.displayClientMessage(Component.translatable("mages_legacy.bond.progress", ritual.feedings, needed(kind)).withStyle(ChatFormatting.GOLD), true);
			return AbilityResult.OK;
		}
		player.displayClientMessage(Component.translatable("mages_legacy.bond.listening", mob.getDisplayName(),
				Component.translatable("mages_legacy.beast." + kind.id() + ".taming", needed(kind))).withStyle(ChatFormatting.GOLD), true);
		return AbilityResult.OK;
	}

	// ---- caring for your own ----

	/** A meal lasts 10 minutes, 20 with Fed Pack; petting teaches a little once in 2 minutes. */
	public static long fedTicks(RaceData data) {
		return data.has(Talents.PACK_FED) ? 24000 : 12000;
	}

	private static InteractionResult care(ServerPlayer player, RaceData data, Mob mob, BeastKind kind, Bond bond, ItemStack stack, long now) {
		if (stack.isEmpty() && player.isShiftKeyDown()) {
			bond.staying = !bond.staying;
			data.dirty = true;
			apply(mob, bond, data);
			mob.getNavigation().stop();
			player.serverLevel().sendParticles(bond.staying ? ParticleTypes.SMOKE : ParticleTypes.HAPPY_VILLAGER, mob.getX(),
					mob.getY() + mob.getBbHeight() + 0.2, mob.getZ(), 5, 0.2, 0.1, 0.2, 0);
			player.displayClientMessage(Component.translatable(bond.staying ? "mages_legacy.bond.stay" : "mages_legacy.bond.follow", mob.getDisplayName())
					.withStyle(ChatFormatting.GOLD), true);
			return InteractionResult.SUCCESS;
		}
		if (stack.isEmpty()) {
			return pet(player, data, mob, bond, now);
		}
		if (kind.treat().test(stack)) {
			return treat(player, data, mob, bond, stack, now);
		}
		// A name tag, a lead and the like work as usual
		return InteractionResult.PASS;
	}

	private static InteractionResult pet(ServerPlayer player, RaceData data, Mob mob, Bond bond, long now) {
		ServerLevel world = player.serverLevel();
		world.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getBbHeight() + 0.2, mob.getZ(), 3, 0.3, 0.2, 0.3, 0);
		mob.playAmbientSound();
		mob.getLookControl().setLookAt(player, 30, 30);
		if (!petReady(bond, now)) {
			return InteractionResult.SUCCESS;
		}
		// A happy beast: a little health back, a little learning, and the Shepherd's voice warms
		bond.pettedAt = now;
		mob.heal(4);
		data.addResource(5);
		beastXp(player, data, bond, mob, 5);
		player.displayClientMessage(Component.translatable("mages_legacy.bond.petted", mob.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
		data.dirty = true;
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult treat(ServerPlayer player, RaceData data, Mob mob, Bond bond, ItemStack stack, long now) {
		boolean hurt = mob.getHealth() < mob.getMaxHealth();
		boolean hungry = bond.fedUntil - now < fedTicks(data) / 2;
		if (!hurt && !hungry) {
			return hint(player, "mages_legacy.bond.full", mob.getDisplayName(), (bond.fedUntil - now) / 1200 + 1);
		}
		consume(player, stack);
		fedEffects(player, mob);
		// Fed Pack: a meal heals it whole; otherwise a third of its health
		mob.heal(data.has(Talents.PACK_FED) ? mob.getMaxHealth() : mob.getMaxHealth() / 3);
		if (bond.fedUntil < now) {
			beastXp(player, data, bond, mob, 5);
		}
		bond.fedUntil = now + fedTicks(data);
		data.dirty = true;
		player.displayClientMessage(Component.translatable("mages_legacy.bond.fed", mob.getDisplayName(), fedTicks(data) / 1200).withStyle(ChatFormatting.GREEN), true);
		return InteractionResult.SUCCESS;
	}

	/** Petting rewards once in two minutes (and the very first time). */
	public static boolean petReady(Bond bond, long now) {
		return bond.pettedAt == 0 || now - bond.pettedAt >= PET_GAP;
	}

	/** A fed beast hits harder: +10%, +15% with Fed Pack. */
	public static float fedBonus(RaceData data, Bond bond, long now) {
		return bond.fedUntil > now ? (data.has(Talents.PACK_FED) ? 1.15f : 1.1f) : 1.0f;
	}

	/** Uses up what was given (a tropical fish bucket leaves its water bucket). */
	private static void consume(ServerPlayer player, ItemStack stack) {
		if (player.getAbilities().instabuild) {
			return;
		}
		if (stack.is(Items.TROPICAL_FISH_BUCKET)) {
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
		} else {
			stack.shrink(1);
		}
	}

	private static void fedEffects(ServerPlayer player, Mob mob) {
		ServerLevel world = player.serverLevel();
		world.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 2, 0.3, 0.2, 0.3, 0);
		world.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.8f, 1.2f);
	}

	/** The bond is made if there's room in the bonds; otherwise the Shepherd hears why not. */
	private static boolean complete(ServerPlayer player, RaceData data, Mob mob, BeastKind kind) {
		if (Shepherd.capacityFree(player, data) < Shepherd.beastCost(kind, data)) {
			player.displayClientMessage(Component.translatable("mages_legacy.ability.no_capacity"), true);
			return false;
		}
		bond(player, data, mob, kind);
		return true;
	}

	private static InteractionResult feed(ServerPlayer player, RaceData data, Mob mob, BeastKind kind, Ritual ritual, ItemStack stack, long now) {
		if (ritual.feedings > 0 && now - ritual.lastFeed < FEED_GAP) {
			return hint(player, "mages_legacy.bond.too_soon", (FEED_GAP - (now - ritual.lastFeed) + 19) / 20);
		}
		int needed = needed(kind);
		boolean last = ritual.feedings + 1 >= needed;
		if (last && kind.taming() == BeastKind.Taming.FEED_BY_FIRE && !nearCampfire(player.serverLevel(), mob.blockPosition())) {
			return hint(player, "mages_legacy.bond.need_fire");
		}
		if (last && Shepherd.capacityFree(player, data) < Shepherd.beastCost(kind, data)) {
			return hint(player, "mages_legacy.ability.no_capacity");
		}
		consume(player, stack);
		ritual.feedings++;
		ritual.lastFeed = now;
		ritual.listenUntil = Math.max(ritual.listenUntil, now + SONG_TICKS);
		data.dirty = true;
		fedEffects(player, mob);
		Progression.addXp(player, data, "shepherd_ritual", 5, 40);
		if (last) {
			bond(player, data, mob, kind);
		} else {
			player.displayClientMessage(Component.translatable("mages_legacy.bond.progress", ritual.feedings, needed), true);
		}
		return InteractionResult.SUCCESS;
	}

	/** One gift seals it (the sleeping fox's berries, the axolotl's fish, the goat's wheat after the headbutts). */
	private static InteractionResult gift(ServerPlayer player, RaceData data, Mob mob, BeastKind kind, ItemStack stack) {
		if (Shepherd.capacityFree(player, data) < Shepherd.beastCost(kind, data)) {
			return hint(player, "mages_legacy.ability.no_capacity");
		}
		consume(player, stack);
		fedEffects(player, mob);
		Progression.addXp(player, data, "shepherd_ritual", 5, 40);
		bond(player, data, mob, kind);
		return InteractionResult.SUCCESS;
	}

	/** The bee wants flowers of different kinds; {@code note} remembers the ones she had. */
	private static InteractionResult flower(ServerPlayer player, RaceData data, Mob mob, BeastKind kind, Ritual ritual, ItemStack stack) {
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		List<String> had = ritual.note.isEmpty() ? List.of() : List.of(ritual.note.split(","));
		if (had.contains(id)) {
			return hint(player, "mages_legacy.bond.another_flower");
		}
		int needed = needed(kind);
		if (had.size() + 1 >= needed) {
			return gift(player, data, mob, kind, stack);
		}
		consume(player, stack);
		ritual.note = ritual.note.isEmpty() ? id : ritual.note + "," + id;
		ritual.feedings = had.size() + 1;
		data.dirty = true;
		fedEffects(player, mob);
		player.displayClientMessage(Component.translatable("mages_legacy.bond.progress", ritual.feedings, needed), true);
		return InteractionResult.SUCCESS;
	}

	/** A bare-handed headbutt at a listening goat: counts, and does no harm. True cancels the blow. */
	public static boolean headbutt(ServerPlayer player, RaceData data, Entity entity) {
		BeastKind kind = BeastKind.of(entity.getType()).orElse(null);
		Ritual ritual = data.shepherd.rituals.get(entity.getUUID());
		long now = player.serverLevel().getGameTime();
		if (kind == null || kind.taming() != BeastKind.Taming.HEADBUTTS || ritual == null || ritual.listenUntil < now
				|| !player.getMainHandItem().isEmpty()) {
			return false;
		}
		if (now - ritual.lastFeed < 10) {
			return true;
		}
		if (ritual.feedings < needed(kind)) {
			ritual.feedings++;
			ritual.lastFeed = now;
			data.dirty = true;
			ServerLevel world = player.serverLevel();
			world.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + entity.getBbHeight() * 0.7, entity.getZ(), 8, 0.3, 0.2, 0.3, 0.1);
			world.playSound(null, entity.blockPosition(), SoundEvents.GOAT_RAM_IMPACT, SoundSource.NEUTRAL, 0.8f, 1.2f);
			player.displayClientMessage(Component.translatable(ritual.feedings >= needed(kind) ? "mages_legacy.bond.headbutts_done"
					: "mages_legacy.bond.progress", ritual.feedings, needed(kind)).withStyle(ChatFormatting.GOLD), true);
		}
		return true;
	}

	/** A monster died near a listening polar bear: it saw the Shepherd's strength. */
	public static void onHunt(ServerPlayer owner, RaceData data, LivingEntity victim) {
		if (data.shepherd.rituals.isEmpty()) {
			return;
		}
		long now = owner.serverLevel().getGameTime();
		for (Mob bear : owner.serverLevel().getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(12), e -> e.isAlive()
				&& BeastKind.of(e.getType()).map(k -> k.taming() == BeastKind.Taming.HUNT).orElse(false))) {
			Ritual ritual = data.shepherd.rituals.get(bear.getUUID());
			if (ritual == null || ritual.listenUntil < now) {
				continue;
			}
			BeastKind kind = BeastKind.of(bear.getType()).orElseThrow();
			ritual.feedings++;
			data.dirty = true;
			if (ritual.feedings >= needed(kind)) {
				complete(owner, data, bear, kind);
			} else {
				owner.displayClientMessage(Component.translatable("mages_legacy.bond.progress", ritual.feedings, needed(kind)).withStyle(ChatFormatting.GOLD), true);
			}
		}
	}

	/**
	 * Once a second: a cat being won by patience counts the seconds the Shepherd crouches by it with fish in hand; a
	 * llama counts the seconds it walks on the Shepherd's lead.
	 */
	private static void patience(ServerPlayer owner, RaceData data, long now) {
		for (Mob llama : owner.serverLevel().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(12), e -> e.isAlive()
				&& e.getLeashHolder() == owner && BeastKind.of(e.getType()).map(k -> k.taming() == BeastKind.Taming.LEAD).orElse(false))) {
			Ritual ritual = data.shepherd.rituals.get(llama.getUUID());
			BeastKind kind = BeastKind.of(llama.getType()).orElseThrow();
			if (ritual == null || ritual.listenUntil < now) {
				continue;
			}
			ritual.feedings++;
			data.dirty = true;
			if (ritual.feedings >= needed(kind)) {
				complete(owner, data, llama, kind);
			} else if (ritual.feedings % 10 == 0) {
				owner.displayClientMessage(Component.translatable("mages_legacy.bond.progress", ritual.feedings, needed(kind)).withStyle(ChatFormatting.GOLD), true);
			}
		}
		for (Mob cat : owner.serverLevel().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(4), e -> e.isAlive()
				&& BeastKind.of(e.getType()).map(k -> k.taming() == BeastKind.Taming.PATIENCE).orElse(false))) {
			Ritual ritual = data.shepherd.rituals.get(cat.getUUID());
			BeastKind kind = BeastKind.of(cat.getType()).orElseThrow();
			if (ritual == null || ritual.listenUntil < now) {
				continue;
			}
			if (!owner.isShiftKeyDown() || !kind.food().test(owner.getMainHandItem())) {
				ritual.feedings = Math.max(0, ritual.feedings - 1);
				continue;
			}
			ritual.feedings++;
			data.dirty = true;
			owner.serverLevel().sendParticles(ParticleTypes.HEART, cat.getX(), cat.getY() + cat.getBbHeight(), cat.getZ(), 1, 0.2, 0.1, 0.2, 0);
			if (ritual.feedings >= needed(kind)) {
				if (complete(owner, data, cat, kind)) {
					consume(owner, owner.getMainHandItem());
				}
			} else {
				owner.displayClientMessage(Component.translatable("mages_legacy.bond.progress", ritual.feedings, needed(kind)).withStyle(ChatFormatting.GOLD), true);
			}
		}
	}

	/** A jukebox with a disc in it within 8 blocks: the parrot dances to it and listens. */
	public static boolean nearJukebox(ServerLevel world, BlockPos pos) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-8, -3, -8), pos.offset(8, 3, 8))) {
			var state = world.getBlockState(p);
			if (state.is(Blocks.JUKEBOX) && state.getValue(JukeboxBlock.HAS_RECORD)) {
				return true;
			}
		}
		return false;
	}

	public static boolean nearCampfire(ServerLevel world, BlockPos pos) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-6, -2, -6), pos.offset(6, 2, 6))) {
			if (CampfireBlock.isLitCampfire(world.getBlockState(p))) {
				return true;
			}
		}
		return false;
	}

	/** The ritual is done: the beast is this Shepherd's for good. */
	public static void bond(ServerPlayer player, RaceData data, Mob mob, BeastKind kind) {
		ShepherdData shepherd = data.shepherd;
		shepherd.rituals.remove(mob.getUUID());
		Bond bond = new Bond();
		bond.entity = mob.getUUID();
		bond.kind = kind.id();
		bond.name = mob.hasCustomName() ? mob.getCustomName().getString() : "";
		shepherd.bonds.add(bond);
		data.dirty = true;
		claim(player.getUUID(), mob, data);
		apply(mob, bond, data);
		ServerLevel world = player.serverLevel();
		world.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getBbHeight() + 0.2, mob.getZ(), 8, 0.4, 0.3, 0.4, 0);
		world.sendParticles(ParticleTypes.FLAME, mob.getX(), mob.getY() + 0.2, mob.getZ(), 20, 0.5, 0.1, 0.5, 0.01);
		// The bond is sealed: a pillar of warm light over the beast
		Fx.pillar(world, mob.position(), Math.max(0.6f, mob.getBbWidth()), 4, 24, Shepherd.GOLD_RGB);
		Fx.ring(world, mob.position(), 0.3f, 4, 18, Shepherd.GOLD_RGB);
		Fx.helix(world, mob, Math.max(0.8f, mob.getBbWidth()), mob.getBbHeight() + 1, 3, true, 24, Shepherd.GOLD_RGB);
		Fx.mark(world, mob.position(), mob, 1.8f, 40, Shepherd.GOLD_RGB, Fx.RIPPLES);
		Fx.burst(world, mob.position().add(0, mob.getBbHeight() / 2, 0), 2.5f, 8, Shepherd.GOLD_RGB);
		Fx.screen(player, 20, Shepherd.GOLD_RGB);
		Fx.aurora(world, mob.position(), 1.8f, 50, Shepherd.GOLD_RGB, Shepherd.SPIRIT_RGB);
		world.playSound(null, mob.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
		player.displayClientMessage(Component.translatable("mages_legacy.bond.done", mob.getDisplayName(),
				Component.translatable("mages_legacy.beast." + kind.id() + ".skill_desc")).withStyle(ChatFormatting.GOLD), false);
		Progression.addXp(player, data, "shepherd_bond", 30, 60);
	}

	/** Makes a loaded beast this Shepherd's: tags, trust, owner, its new goals. */
	static void claim(UUID owner, Mob mob, RaceData data) {
		mob.addTag(Creatures.BOND_TAG);
		mob.getTags().removeIf(t -> t.startsWith(Creatures.OWNER_TAG));
		mob.addTag(Creatures.OWNER_TAG + owner);
		mob.setPersistenceRequired();
		Creatures.registerBeast(mob, owner);
		if (mob instanceof TamableAnimal tame) {
			tame.setOwnerUUID(owner);
			tame.setTame(true, true);
		} else {
			// Foxes and polar bears fight for their Shepherd; bees, axolotls, goats and frogs help in their own ways
			Creatures.teach(mob, mob.getType() == EntityType.FOX || mob.getType() == EntityType.POLAR_BEAR);
		}
		if (mob instanceof Fox fox) {
			((FoxEntityInvoker) fox).mages_legacy$trust(owner);
		}
		if (mob instanceof Bee bee) {
			// A bonded bee stays with its Shepherd instead of vanishing into a hive
			bee.setStayOutOfHiveCountdown(Integer.MAX_VALUE);
		}
	}

	/** Health from its level, Thick Fur, the leader's strength with the Alpha crown; staying put or following. */
	static void apply(Mob mob, Bond bond, RaceData data) {
		if (bond.staying) {
			mob.addTag(Creatures.STAY_TAG);
		} else {
			mob.getTags().remove(Creatures.STAY_TAG);
		}
		if (mob instanceof TamableAnimal tame && tame.isOrderedToSit() != bond.staying) {
			tame.setOrderedToSit(bond.staying);
			tame.setInSittingPose(bond.staying);
		}
		modifier(mob, Attributes.MAX_HEALTH, LEVEL_HEALTH, "bond_level", bond.level - 1, true);
		double base = mob.getAttributeBaseValue(Attributes.MAX_HEALTH);
		modifier(mob, Attributes.MAX_HEALTH, THICK_FUR, "thick_fur", Math.round(base * 0.3), data.has(Talents.PACK_THICK));
		boolean alpha = isAlpha(data, bond);
		modifier(mob, Attributes.MAX_HEALTH, ALPHA_HEALTH, "alpha", Math.round(base * 0.5), alpha);
		modifier(mob, Attributes.ATTACK_DAMAGE, ALPHA_DAMAGE, "alpha", 4, alpha);
		if (alpha) {
			mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, true, false));
		}
	}

	/** This beast leads the pack with the Alpha crown. */
	static boolean isAlpha(RaceData data, Bond bond) {
		return data.has(Talents.PACK_ALPHA) && alpha(data) == bond;
	}

	/**
	 * How strong a beast's skill is: a step every 5 levels, one more with Gift Unfolded, two more for the Alpha's
	 * leader. 0 for a fresh beast, up to 7.
	 */
	public static int power(RaceData data, Bond bond) {
		return bond.level / 5 + (data.has(Talents.PACK_SKILL) ? 1 : 0) + (isAlpha(data, bond) ? 2 : 0);
	}

	private static void modifier(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, net.minecraft.resources.ResourceLocation id, String name,
			double amount, boolean want) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		AttributeModifier current = instance.getModifier(id);
		if (current != null && (!want || current.amount() != amount)) {
			instance.removeModifier(id);
			current = null;
		}
		if (want && current == null && amount != 0) {
			instance.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
		}
		if (entity.getHealth() > entity.getMaxHealth()) {
			entity.setHealth(entity.getMaxHealth());
		}
	}

	/** The pack's leader: the one the Shepherd chose in the bestiary, or else the highest-level beast. */
	static Bond alpha(RaceData data) {
		Bond best = null;
		for (Bond b : data.shepherd.bonds) {
			if (b.leader) {
				return b;
			}
			if (best == null || b.level > best.level) {
				best = b;
			}
		}
		return best;
	}

	/** Makes this beast the leader (or, if it already is, nobody). */
	public static void toggleLeader(RaceData data, int index) {
		if (index < 0 || index >= data.shepherd.bonds.size()) {
			return;
		}
		Bond chosen = data.shepherd.bonds.get(index);
		boolean was = chosen.leader;
		data.shepherd.bonds.forEach(b -> b.leader = false);
		chosen.leader = !was;
		data.dirty = true;
	}

	/** A name for a beast, from the bestiary: kept in the bond and shown over the beast. */
	public static void rename(ServerPlayer owner, RaceData data, int index, String name) {
		if (index < 0 || index >= data.shepherd.bonds.size()) {
			return;
		}
		Bond bond = data.shepherd.bonds.get(index);
		bond.name = name.strip();
		if (bond.name.length() > 24) {
			bond.name = bond.name.substring(0, 24);
		}
		if (!bond.resting() && owner.serverLevel().getEntity(bond.entity) instanceof Mob mob) {
			mob.setCustomName(bond.name.isEmpty() ? null : Component.literal(bond.name));
		} else if (bond.resting()) {
			if (bond.name.isEmpty()) {
				bond.body.remove("CustomName");
			} else {
				bond.body.putString("CustomName", Component.Serializer.toJson(Component.literal(bond.name), owner.registryAccess()));
			}
		}
		data.dirty = true;
	}

	public static int maxLevel(RaceData data) {
		return data.has(Talents.PACK_LEVELS) ? 20 : 10;
	}

	/** Experience to go from {@code level} to the next. */
	public static int xpForNext(int level) {
		return 20 * level;
	}

	/**
	 * A bonded beast learns: from monsters killed near it (more if it struck the blow), from using its skill, and a
	 * little from simply walking with its Shepherd.
	 */
	public static void beastXp(ServerPlayer owner, RaceData data, Bond bond, Mob mob, int amount) {
		if (bond.level >= maxLevel(data)) {
			return;
		}
		bond.xp += data.has(Talents.PACK_LEVELS) ? amount * 2 : amount;
		while (bond.level < maxLevel(data) && bond.xp >= xpForNext(bond.level)) {
			bond.xp -= xpForNext(bond.level);
			bond.level++;
			apply(mob, bond, data);
			mob.heal(4);
			owner.displayClientMessage(Component.translatable("mages_legacy.bond.level", mob.getDisplayName(), bond.level).withStyle(ChatFormatting.GREEN), true);
			if (mob.level() instanceof ServerLevel world) {
				world.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 10, 0.4, 0.4, 0.4, 0);
			}
		}
		data.dirty = true;
	}

	// ---- falling and coming back ----

	public static long restTicks(RaceData data) {
		return data.has(Talents.PACK_INSTANT) ? 600 : 6000;
	}

	/** A bonded beast would die: it rests instead, and comes back to its Shepherd later. False cancels the death. */
	public static boolean allowDeath(LivingEntity entity) {
		if (!(entity instanceof Mob mob) || entity.getServer() == null || !entity.getTags().contains(Creatures.BOND_TAG)) {
			return true;
		}
		UUID ownerId = Creatures.taggedOwner(entity);
		RaceData data = ownerId == null ? null : RaceState.get(entity.getServer()).data(ownerId);
		Bond bond = data == null ? null : data.shepherd.bond(entity.getUUID()).orElse(null);
		if (bond == null) {
			return true;
		}
		ServerLevel here = (ServerLevel) mob.level();
		// Not Today: once in 5 minutes a beast shrugs off the killing blow
		if (data.has(Talents.PACK_NOT_TODAY) && (bond.savedAt == 0 || here.getGameTime() - bond.savedAt >= NOT_TODAY_GAP)) {
			bond.savedAt = here.getGameTime();
			data.dirty = true;
			mob.setHealth(mob.getMaxHealth() / 2);
			mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 3));
			here.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, mob.getX(), mob.getY() + 1, mob.getZ(), 30, 0.4, 0.6, 0.4, 0.3);
			here.playSound(null, mob.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.NEUTRAL, 0.6f, 1.3f);
			ServerPlayer owner = entity.getServer().getPlayerList().getPlayer(ownerId);
			if (owner != null) {
				owner.displayClientMessage(Component.translatable("mages_legacy.bond.not_today", mob.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
			}
			return false;
		}
		mob.setHealth(mob.getMaxHealth());
		mob.removeAllEffects();
		mob.clearFire();
		CompoundTag body = new CompoundTag();
		mob.saveAsPassenger(body);
		bond.body = body;
		bond.restingUntil = ((ServerLevel) mob.level()).getGameTime() + restTicks(data);
		data.dirty = true;
		ServerLevel world = (ServerLevel) mob.level();
		world.sendParticles(ParticleTypes.CLOUD, mob.getX(), mob.getY() + 0.5, mob.getZ(), 15, 0.3, 0.3, 0.3, 0.02);
		ServerPlayer owner = entity.getServer().getPlayerList().getPlayer(ownerId);
		if (owner != null) {
			Shepherd.onLoss(owner, data);
			long rest = restTicks(data);
			Component when = rest >= 1200 ? Component.translatable("mages_legacy.time.minutes", rest / 1200) : Component.translatable("mages_legacy.time.seconds", rest / 20);
			owner.displayClientMessage(Component.translatable("mages_legacy.bond.rests", mob.getDisplayName(), when).withStyle(ChatFormatting.YELLOW), false);
		}
		Creatures.forgetBeast(mob.getUUID());
		mob.discard();
		return false;
	}

	/** Once a second for an online Shepherd: rested beasts come back, nearby ones use their skills. */
	public static void tickOwner(ServerPlayer owner, RaceData data) {
		ServerLevel world = owner.serverLevel();
		long time = world.getGameTime();
		patience(owner, data, time);
		for (Bond bond : data.shepherd.bonds) {
			if (bond.resting()) {
				if (time >= bond.restingUntil) {
					wake(owner, data, bond);
				}
				continue;
			}
			if (world.getEntity(bond.entity) instanceof Mob mob && mob.isAlive()) {
				apply(mob, bond, data);
				skill(owner, data, bond, mob, time);
				// A fed beast mends by itself: a heart every 5 seconds (every 2 with Fed Pack)
				if (bond.fedUntil > time && time % (data.has(Talents.PACK_FED) ? 40 : 100) < 20) {
					mob.heal(2);
				}
				// A name given with a name tag becomes the bond's name too
				String name = mob.hasCustomName() ? mob.getCustomName().getString() : "";
				if (!name.equals(bond.name)) {
					bond.name = name;
					data.dirty = true;
				}
				// Walking with its Shepherd teaches a little too
				if (time % 600 < 20 && mob.distanceTo(owner) < 16) {
					beastXp(owner, data, bond, mob, 2);
				}
			}
		}
		// The Alpha leads: the other beasts near the champion fight harder
		Bond alpha = data.has(Talents.PACK_ALPHA) ? alpha(data) : null;
		if (alpha != null && !alpha.resting() && world.getEntity(alpha.entity) instanceof Mob leader && leader.isAlive()) {
			for (Bond bond : data.shepherd.bonds) {
				if (bond != alpha && !bond.resting() && world.getEntity(bond.entity) instanceof Mob mob && mob.distanceTo(leader) < 16) {
					mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 0, true, false));
					mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, true, false));
				}
			}
		}
	}

	private static void wake(ServerPlayer owner, RaceData data, Bond bond) {
		ServerLevel world = owner.serverLevel();
		Entity entity = EntityType.loadEntityRecursive(bond.body, world, e -> {
			Vec3 spot = Creatures.near(owner);
			e.moveTo(spot.x, spot.y, spot.z, e.getYRot(), e.getXRot());
			return e;
		});
		if (!(entity instanceof Mob mob)) {
			return;
		}
		// A fresh uuid: the body that fell may still be on the world's books for a tick, and a clash would refuse it
		mob.setUUID(UUID.randomUUID());
		bond.body = null;
		bond.restingUntil = 0;
		bond.entity = mob.getUUID();
		data.dirty = true;
		world.tryAddFreshEntityWithPassengers(mob);
		claim(owner.getUUID(), mob, data);
		world.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 5, 0.3, 0.3, 0.3, 0);
		owner.displayClientMessage(Component.translatable("mages_legacy.bond.back", mob.getDisplayName()).withStyle(ChatFormatting.GREEN), true);
	}

	/**
	 * What each beast does for its Shepherd. Its {@link #power} (levels, Gift Unfolded, the leader) widens the reach,
	 * shortens the wait and strengthens the effect; doing something useful teaches the beast a little.
	 */
	private static void skill(ServerPlayer owner, RaceData data, Bond bond, Mob mob, long time) {
		int p = power(data, bond);
		ServerLevel world = owner.serverLevel();
		double dist = mob.distanceTo(owner);
		// Goats, frogs and axolotls think with a brain, not with goals: when they fall behind they jump to the Shepherd
		// (so do bats and turtles, the one flutters and the other is too slow to keep up)
		boolean brainy = mob.getType() == EntityType.GOAT || mob.getType() == EntityType.FROG || mob.getType() == EntityType.AXOLOTL
				|| mob.getType() == EntityType.BAT || mob.getType() == EntityType.TURTLE;
		boolean swimmer = mob.getType() == EntityType.AXOLOTL || mob.getType() == EntityType.DOLPHIN;
		// Quick Paws: every beast runs faster and none is ever left behind
		boolean fast = data.has(Talents.PACK_FAST);
		if (fast) {
			mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, true, false));
		}
		if (!bond.staying && (brainy || swimmer || fast) && dist > (fast ? 16 : 12) && (!swimmer || owner.isInWater())) {
			Vec3 spot = Creatures.near(owner);
			mob.moveTo(spot.x, spot.y, spot.z, mob.getYRot(), mob.getXRot());
		}
		if (dist > 32) {
			return;
		}
		switch (bond.kind) {
			case "wolf" -> {
				if (dist < 16) {
					mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, Math.min(2, p / 2), true, false));
				}
			}
			case "cat" -> {
				int seen = 0;
				for (LivingEntity m : world.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(12 + 3 * p), e -> e instanceof Enemy)) {
					m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, true, false));
					seen++;
				}
				if (dist < 16 && world.isNight()) {
					owner.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false, true));
				}
				if (seen > 0 && time % 200 < 20) {
					beastXp(owner, data, bond, mob, 2);
				}
			}
			case "fox" -> {
				if (time % 60 < 20 && dist < 24) {
					for (ItemEntity item : world.getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(8 + 2 * p),
							i -> i.isAlive() && !i.hasPickUpDelay() && i.distanceToSqr(owner) > 36)) {
						world.sendParticles(ParticleTypes.POOF, item.getX(), item.getY() + 0.2, item.getZ(), 3, 0.1, 0.1, 0.1, 0);
						item.moveTo(owner.getX(), owner.getY() + 0.5, owner.getZ(), 0, 0);
					}
				}
				// Now and then she digs something up: every 5 minutes, sooner as she grows
				int every = Math.max(1800, 6000 - 700 * p);
				if (dist < 24 && Math.floorMod(time + mob.getUUID().hashCode(), every) < 20) {
					ItemStack find = FoxFinds.roll(mob.getRandom());
					world.addFreshEntity(new ItemEntity(world, owner.getX(), owner.getY() + 0.5, owner.getZ(), find));
					world.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + 0.5, mob.getZ(), 8, 0.3, 0.3, 0.3, 0);
					owner.displayClientMessage(Component.translatable("mages_legacy.beast.fox.found", find.getHoverName()).withStyle(ChatFormatting.GOLD), true);
					beastXp(owner, data, bond, mob, 10);
				}
			}
			case "bee" -> {
				if (time % Math.max(60, 160 - 20 * p) < 20) {
					double r = 6 + 2 * p;
					int amp = p >= 3 ? 1 : 0;
					AABB area = mob.getBoundingBox().inflate(r);
					boolean healed = false;
					if (owner.getBoundingBox().intersects(area)) {
						healed = owner.getHealth() < owner.getMaxHealth() || owner.hasEffect(MobEffects.POISON);
						owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, amp, true, true));
						owner.removeEffect(MobEffects.POISON);
						owner.removeEffect(MobEffects.WITHER);
					}
					for (LivingEntity c : world.getEntitiesOfClass(LivingEntity.class, area, e -> Creatures.isCreatureOf(e, owner.getUUID()))) {
						healed |= c.getHealth() < c.getMaxHealth();
						c.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, amp, true, true));
					}
					world.sendParticles(ParticleTypes.WAX_ON, mob.getX(), mob.getY() + 0.5, mob.getZ(), 6, r * 0.3, 0.4, r * 0.3, 0);
					if (healed) {
						beastXp(owner, data, bond, mob, 3);
					}
				}
			}
			case "axolotl" -> {
				if (owner.isInWater() && dist < 12 + 4 * p) {
					owner.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 300, 0, true, true));
					owner.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 60, 0, true, true));
					owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, p >= 3 ? 1 : 0, true, true));
					owner.removeEffect(MobEffects.POISON);
					if (time % 200 < 20) {
						beastXp(owner, data, bond, mob, 2);
					}
				}
			}
			case "goat" -> {
				// Rams the nearest monster at its Shepherd's side; more often, harder and from farther as it grows
				if (time % Math.max(30, 80 - 10 * p) < 20 && dist < 16) {
					LivingEntity target = nearestMonster(world, owner, 6 + p);
					if (target != null) {
						Vec3 away = target.position().subtract(owner.position()).normalize();
						Vec3 spot = target.position().subtract(away.scale(1.2));
						mob.moveTo(spot.x, target.getY(), spot.z, mob.getYRot(), mob.getXRot());
						target.hurt(world.damageSources().mobAttack(mob), 4 + p);
						target.knockback(1.4 + 0.15 * p, -away.x, -away.z);
						world.playSound(null, target.blockPosition(), SoundEvents.GOAT_RAM_IMPACT, SoundSource.NEUTRAL, 1.0f, 1.0f);
						world.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1, target.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
						beastXp(owner, data, bond, mob, 2);
					}
				}
			}
			case "polar_bear" -> {
				// The tank: monsters going for the Shepherd turn on the bear
				mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, Math.min(2, p / 2), true, false));
				if (dist < 16) {
					int taken = 0;
					for (Mob m : world.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(8 + 2 * p),
							e -> e instanceof Enemy && e.getTarget() == owner)) {
						m.setTarget(mob);
						taken++;
					}
					if (taken > 0) {
						beastXp(owner, data, bond, mob, 2);
					}
				}
			}
			case "parrot" -> {
				// Cries out when something hunts the Shepherd, and catches it when it falls
				for (Mob m : world.getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(16 + 4 * p),
						e -> e instanceof Enemy && e.getTarget() == owner)) {
					if (!m.hasEffect(MobEffects.GLOWING)) {
						owner.displayClientMessage(Component.translatable("mages_legacy.beast.parrot.warn", m.getDisplayName()).withStyle(ChatFormatting.RED), true);
						world.playSound(null, mob.blockPosition(), SoundEvents.PARROT_IMITATE_CREEPER, SoundSource.NEUTRAL, 0.8f, 1.2f);
						beastXp(owner, data, bond, mob, 2);
					}
					m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, true, false));
				}
				if (dist < 16 && owner.fallDistance > 4) {
					owner.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, true, false, true));
				}
			}
			case "frog" -> {
				// The tongue pulls the nearest monster in and slows it; small slimes it simply eats
				if (time % Math.max(20, 60 - 8 * p) < 20) {
					LivingEntity target = nearestMonster(world, mob, 8 + 2 * p);
					if (target instanceof Slime slime && slime.getSize() <= 1 + p / 3) {
						world.sendParticles(ParticleTypes.ITEM_SLIME, slime.getX(), slime.getY(), slime.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
						slime.discard();
						beastXp(owner, data, bond, mob, 4);
					} else if (target != null) {
						Vec3 pull = mob.position().subtract(target.position()).normalize().scale(0.9);
						target.push(pull.x, 0.25, pull.z);
						target.hurtMarked = true;
						target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, Math.min(3, 1 + p / 2)));
						world.playSound(null, mob.blockPosition(), SoundEvents.FROG_TONGUE, SoundSource.NEUTRAL, 1.0f, 1.0f);
						beastXp(owner, data, bond, mob, 2);
					}
				}
			}
			case "llama" -> {
				// Spits at the nearest monster near its Shepherd
				if (time % Math.max(20, 60 - 8 * p) < 20 && dist < 16 && mob instanceof Llama llama) {
					LivingEntity target = nearestMonster(world, owner, 10 + p);
					if (target != null) {
						LlamaSpit spit = new LlamaSpit(world, llama);
						Vec3 to = target.getEyePosition().subtract(spit.position());
						spit.shoot(to.x, to.y + to.horizontalDistance() * 0.1, to.z, 1.5f, 2);
						world.addFreshEntity(spit);
						target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, Math.min(3, 1 + p / 2)));
						world.playSound(null, mob.blockPosition(), SoundEvents.LLAMA_SPIT, SoundSource.NEUTRAL, 1.0f, 1.0f);
						beastXp(owner, data, bond, mob, 2);
					}
				}
			}
			case "rabbit" -> {
				if (dist < 16) {
					owner.addEffect(new MobEffectInstance(MobEffects.LUCK, 60, Math.min(2, p / 2), true, false, true));
					owner.addEffect(new MobEffectInstance(MobEffects.JUMP, 60, Math.min(2, p / 2), true, false, true));
				}
			}
			case "turtle" -> {
				// The shell: on the brink of death the Shepherd hardens; the wait shortens as the turtle grows
				long ticks = owner.getServer().getTickCount();
				if (dist < 24 && owner.getHealth() < owner.getMaxHealth() * 0.3f && data.readyAt.getOrDefault(TURTLE_SLOT, 0L) <= ticks) {
					owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, Math.min(3, 1 + p / 2), true, true, true));
					owner.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1 + p / 3, true, true, true));
					data.readyAt.put(TURTLE_SLOT, ticks + Math.max(600, 1800 - 200 * p));
					world.playSound(null, owner.blockPosition(), SoundEvents.ARMOR_EQUIP_TURTLE.value(), SoundSource.PLAYERS, 1.0f, 0.8f);
					owner.displayClientMessage(Component.translatable("mages_legacy.beast.turtle.shell").withStyle(ChatFormatting.GREEN), true);
					beastXp(owner, data, bond, mob, 10);
				}
			}
			case "dolphin" -> {
				if (owner.isInWater() && dist < 16 + 4 * p) {
					owner.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 300, 0, true, false, true));
					owner.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 60, 0, true, false, true));
					if (time % 200 < 20) {
						beastXp(owner, data, bond, mob, 2);
					}
				}
			}
			case "bat" -> {
				// Echolocation: underground, monsters around glow
				if (dist < 24 && !world.canSeeSky(owner.blockPosition())) {
					int heard = 0;
					for (LivingEntity m : world.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(20 + 4 * p),
							e -> e instanceof Enemy)) {
						m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, true, false));
						heard++;
					}
					if (heard > 0 && time % 200 < 20) {
						beastXp(owner, data, bond, mob, 2);
					}
				}
			}
			default -> {
			}
		}
	}

	@Nullable
	private static LivingEntity nearestMonster(ServerLevel world, Entity center, double radius) {
		LivingEntity best = null;
		for (LivingEntity m : world.getEntitiesOfClass(LivingEntity.class, center.getBoundingBox().inflate(radius),
				e -> e instanceof Enemy && e.isAlive() && Creatures.ownerOf(e) == null)) {
			if (best == null || m.distanceToSqr(center) < best.distanceToSqr(center)) {
				best = m;
			}
		}
		return best;
	}

	/** A bonded beast's blow: the wolf's bite slows. */
	public static void onBeastHit(Entity beast, LivingEntity victim) {
		if (beast.getType() == EntityType.WOLF) {
			victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
		}
	}

	/** Polar bear guard: a Shepherd with its bonded bear close by takes a fifth less damage. */
	public static boolean bearGuards(ServerPlayer owner, RaceData data) {
		for (Bond bond : data.shepherd.bonds) {
			if (bond.kind.equals("polar_bear") && !bond.resting() && owner.serverLevel().getEntity(bond.entity) instanceof Mob bear
					&& bear.isAlive() && bear.distanceTo(owner) < 6) {
				return true;
			}
		}
		return false;
	}

	/** When a bonded beast loads with its chunk: register it and give back its goals (goals aren't saved). */
	public static void onLoad(Entity entity, RaceData ownerData, UUID owner) {
		if (entity instanceof Mob mob && ownerData.shepherd.bond(entity.getUUID()).isPresent()) {
			claim(owner, mob, ownerData);
		}
	}

	/** The Shepherd changed race: the beasts are free again (resting ones come back first). */
	public static void releaseAll(ServerPlayer owner, RaceData data) {
		for (Bond bond : List.copyOf(data.shepherd.bonds)) {
			if (bond.resting()) {
				wake(owner, data, bond);
			}
			if (owner.serverLevel().getEntity(bond.entity) instanceof Mob mob) {
				mob.getTags().remove(Creatures.BOND_TAG);
				mob.getTags().removeIf(t -> t.startsWith(Creatures.OWNER_TAG));
				Creatures.forgetBeast(mob.getUUID());
			}
		}
	}
}
