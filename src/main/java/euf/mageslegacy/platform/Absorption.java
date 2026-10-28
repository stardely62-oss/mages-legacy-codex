package euf.mageslegacy.platform;

import euf.mageslegacy.MagesLegacy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** 1.21 caps absorption by an attribute; ability rewards retain their original eight-point pool. */
public final class Absorption {
    private static final ResourceLocation LIMIT = MagesLegacy.id("ability_absorption_limit");
    public static void add(ServerPlayer player, float amount) {
        var attribute = player.getAttribute(Attributes.MAX_ABSORPTION);
        if (attribute != null && attribute.getModifier(LIMIT) == null)
            attribute.addPermanentModifier(new AttributeModifier(LIMIT, 8, AttributeModifier.Operation.ADD_VALUE));
        player.setAbsorptionAmount(Math.min(8, player.getAbsorptionAmount() + amount));
    }
    public static void tick(ServerPlayer player) {
        if (player.getAbsorptionAmount() > 0) return;
        var attribute = player.getAttribute(Attributes.MAX_ABSORPTION);
        if (attribute != null) attribute.removeModifier(LIMIT);
    }
}
