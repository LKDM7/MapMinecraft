package neoforge.mapminecraft.item;

import neoforge.mapminecraft.Mapminecraft;
import neoforge.mapminecraft.block.LiveMapBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Links projectors into a composite group. Right-click a projector to pick it as the master, then
 * right-click other projectors to add or remove them from that master's combined hologram. Sneak +
 * right-click a projector to clear its links. The selected master is remembered on the item itself.
 *
 * <p>Tip: after linking, switch the slaves' own holograms off in their GUI so only the master shows
 * the combined map.
 */
public class LiveMapLinkerItem extends Item {

    public LiveMapLinkerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        if (!(level.getBlockEntity(pos) instanceof LiveMapBlockEntity be)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide || player == null) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        ItemStack stack = context.getItemInHand();

        // Sneak: clear the clicked projector's own links.
        if (player.isShiftKeyDown()) {
            be.clearLinks();
            feedback(player, "item.mapminecraft.linker.cleared");
            return InteractionResult.SUCCESS;
        }

        GlobalPos selected = stack.get(Mapminecraft.LINK_TARGET.get());
        boolean sameDimension = selected != null && selected.dimension().equals(level.dimension());

        if (selected == null || !sameDimension) {
            // No master yet (or it was in another dimension): pick this projector as the master.
            stack.set(Mapminecraft.LINK_TARGET.get(), GlobalPos.of(level.dimension(), pos.immutable()));
            feedback(player, "item.mapminecraft.linker.master", pos.getX(), pos.getY(), pos.getZ());
        } else if (selected.pos().equals(pos)) {
            // Clicking the master again clears the selection.
            stack.remove(Mapminecraft.LINK_TARGET.get());
            feedback(player, "item.mapminecraft.linker.deselected");
        } else if (level.getBlockEntity(selected.pos()) instanceof LiveMapBlockEntity master) {
            boolean added = master.toggleLink(pos);
            if (added) {
                feedback(player, "item.mapminecraft.linker.linked", master.getLinks().size());
            } else {
                feedback(player, "item.mapminecraft.linker.unlinked", master.getLinks().size());
            }
        } else {
            // The remembered master is gone: adopt the clicked projector as the new master.
            stack.set(Mapminecraft.LINK_TARGET.get(), GlobalPos.of(level.dimension(), pos.immutable()));
            feedback(player, "item.mapminecraft.linker.master", pos.getX(), pos.getY(), pos.getZ());
        }
        return InteractionResult.SUCCESS;
    }

    private static void feedback(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }
}
