package io.github.neko0115.moxuebridge.workspace;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class WorkspaceWandListener
implements Listener {

    public static final String WAND_NAME =
            "墨雪設定棍";

    private static final String WAND_VERSION =
            "v1";

    private final WorkspaceSelectionStore store;
    private final NamespacedKey wandKey;

    public WorkspaceWandListener(
            JavaPlugin plugin,
            WorkspaceSelectionStore store) {

        this.store =
                Objects.requireNonNull(store);

        this.wandKey =
                new NamespacedKey(
                        Objects.requireNonNull(plugin),
                        "workspace_wand");
    }

    @EventHandler(
            priority = EventPriority.HIGH,
            ignoreCancelled = true)
    public void onPlayerInteract(
            PlayerInteractEvent event) {

        if (event.getHand()
                != EquipmentSlot.HAND) {

            return;
        }

        Block clicked =
                event.getClickedBlock();

        if (clicked == null
                || !isWorkspaceWand(
                        event.getItem())) {

            return;
        }

        Action action =
                event.getAction();

        boolean pointA;
        if (action
                == Action.LEFT_CLICK_BLOCK) {

            pointA = true;

        } else if (
                action
                        == Action.RIGHT_CLICK_BLOCK) {

            pointA = false;

        } else {
            return;
        }

        event.setCancelled(true);

        var player =
                event.getPlayer();

        var world =
                clicked.getWorld();

        var point =
                new WorkspaceSelectionPoint(
                        clicked.getX(),
                        clicked.getY(),
                        clicked.getZ());

        WorkspaceSelection completed;
        if (pointA) {
            completed =
                    store.setPointA(
                            world.getUID()
                                    .toString(),
                            dimensionOf(world),
                            player.getUniqueId()
                                    .toString(),
                            player.getName(),
                            point);
        } else {
            completed =
                    store.setPointB(
                            world.getUID()
                                    .toString(),
                            dimensionOf(world),
                            player.getUniqueId()
                                    .toString(),
                            player.getName(),
                            point);
        }

        player.sendActionBar(
                Component.text(
                        "墨雪設定棍 "
                                + (pointA
                                        ? "A"
                                        : "B")
                                + ": "
                                + point.x()
                                + ", "
                                + point.y()
                                + ", "
                                + point.z()));

        if (completed != null) {
            player.sendActionBar(
                    Component.text(
                            "墨雪選區完成 #"
                                    + completed.generation()));
        }
    }

    private boolean isWorkspaceWand(
            ItemStack item) {

        if (item == null
                || item.getType()
                        != Material.STICK) {

            return false;
        }

        var meta = item.getItemMeta();
        if (meta == null
                || !meta.hasDisplayName()) {

            return false;
        }

        var displayName =
                meta.displayName();

        if (displayName == null) {
            return false;
        }

        String plainName =
                PlainTextComponentSerializer
                        .plainText()
                        .serialize(displayName);

        if (!WAND_NAME.equals(plainName)) {
            return false;
        }

        String marker =
                meta.getPersistentDataContainer()
                        .get(
                                wandKey,
                                PersistentDataType.STRING);

        return marker == null
                || WAND_VERSION.equals(marker);
    }

    private static String dimensionOf(
            World world) {

        return switch (world.getEnvironment()) {
            case NORMAL -> "overworld";
            case NETHER -> "the_nether";
            case THE_END -> "the_end";
            case CUSTOM ->
                    world.getKey()
                            .toString();
        };
    }
}
