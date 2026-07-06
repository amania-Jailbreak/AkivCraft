package dev.akivcraft.loader;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

public final class AkivCraftItem extends Item {
    private final String itemId;
    private final List<ItemUseAction> useActions;
    private final ItemUseAnimation animation;
    private final int duration;

    public AkivCraftItem(Properties properties, String itemId, List<ItemUseAction> useActions, ItemUseAnimation animation, int duration) {
        super(properties);
        this.itemId = itemId;
        this.useActions = useActions;
        this.animation = animation != null ? animation : ItemUseAnimation.NONE;
        this.duration = duration;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        var result = ItemUseHandler.dispatch(useActions, itemId, player.getItemInHand(hand), level, player, hand);
        return result == InteractionResult.PASS ? super.use(level, player, hand) : result;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (player != null) {
            var result = ItemUseHandler.dispatch(useActions, itemId, context.getItemInHand(), context.getLevel(), player, context.getHand());
            if (result != InteractionResult.PASS) return result;
        }
        return super.useOn(context);
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return animation;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return duration;
    }
}
