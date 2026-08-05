package cn.mythicland.lib.menu;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Shared single-view inventory lifecycle and click protection for dependent plugins.
 */
public final class MenuService implements Listener, AutoCloseable {

    private final JavaPlugin owner;
    private final Map<UUID, MenuHolder> openMenus = new HashMap<>();

    /**
     * Creates and registers the shared menu listener.
     *
     * @param owner the plugin owning the listener lifecycle
     */
    public MenuService(JavaPlugin owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
        owner.getServer().getPluginManager().registerEvents(this, owner);
    }

    /**
     * Opens a view for a player. Calls from an asynchronous thread are moved to the primary thread.
     *
     * @param player the viewer
     * @param view   the view to open
     */
    public void open(Player player, MenuView view) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(view, "view");
        if (!Bukkit.isPrimaryThread()) {
            owner.getServer().getScheduler().runTask(owner, () -> openNow(player, view));
            return;
        }
        openNow(player, view);
    }

    /**
     * Refreshes the current view without allowing inventory transfer operations.
     *
     * @param player the viewer
     */
    public void refresh(Player player) {
        if (player == null) return;
        if (!Bukkit.isPrimaryThread()) {
            owner.getServer().getScheduler().runTask(owner, () -> refresh(player));
            return;
        }
        MenuHolder holder = openMenus.get(player.getUniqueId());
        if (holder == null) return;
        refreshNow(player, holder);
    }

    /**
     * Closes the current menu for a player.
     *
     * @param player the viewer
     */
    public void close(Player player) {
        if (player == null) return;
        if (!Bukkit.isPrimaryThread()) {
            owner.getServer().getScheduler().runTask(owner, () -> close(player));
            return;
        }
        openMenus.remove(player.getUniqueId());
        player.closeInventory();
    }

    /**
     * Returns whether the player currently has a Lib-managed menu open.
     *
     * @param playerUniqueId the player UUID
     * @return true when a managed menu is open
     */
    public boolean hasOpenMenu(UUID playerUniqueId) {
        return playerUniqueId != null && openMenus.containsKey(playerUniqueId);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        MenuHolder holder = holder(event.getView().getTopInventory());
        if (holder == null || !holder.viewerUniqueId.equals(player.getUniqueId())) return;
        event.setCancelled(true);
        if (event.getRawSlot() < 0) return;
        holder.view.handleClick(player, event, this);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void handleDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (holder(event.getView().getTopInventory()) == null) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void handleClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        MenuHolder holder = holder(event.getInventory());
        if (holder == null) return;
        openMenus.remove(player.getUniqueId(), holder);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void handleQuit(PlayerQuitEvent event) {
        openMenus.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Closes all managed views and releases the session registry.
     */
    @Override
    public void close() {
        if (!Bukkit.isPrimaryThread()) {
            owner.getServer().getScheduler().runTask(owner, this::close);
            return;
        }
        for (UUID playerUniqueId : Set.copyOf(openMenus.keySet())) {
            Player player = Bukkit.getPlayer(playerUniqueId);
            if (player != null) player.closeInventory();
        }
        openMenus.clear();
    }

    private void openNow(Player player, MenuView view) {
        MenuHolder previous = openMenus.remove(player.getUniqueId());
        if (previous != null) player.closeInventory();

        int size = view.size(player);
        if (size < 9 || size > 54 || size % 9 != 0) {
            throw new IllegalArgumentException("Menu size must be a multiple of nine between 9 and 54");
        }
        MenuHolder holder = new MenuHolder(player.getUniqueId(), view);
        Inventory inventory = Bukkit.createInventory(holder, size, view.title(player));
        holder.inventory = inventory;
        openMenus.put(player.getUniqueId(), holder);
        view.render(player, inventory);
        player.openInventory(inventory);
    }

    private void refreshNow(Player player, MenuHolder holder) {
        int size = holder.view.size(player);
        String title = holder.view.title(player);
        if (size != holder.inventory.getSize() || !title.equals(player.getOpenInventory().getTitle())) {
            openNow(player, holder.view);
            return;
        }
        holder.inventory.clear();
        holder.view.render(player, holder.inventory);
        player.updateInventory();
    }

    private MenuHolder holder(Inventory inventory) {
        if (inventory == null) return null;
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof MenuHolder menuHolder ? menuHolder : null;
    }

    private static final class MenuHolder implements InventoryHolder {
        private final UUID viewerUniqueId;
        private final MenuView view;
        private Inventory inventory;

        private MenuHolder(UUID viewerUniqueId, MenuView view) {
            this.viewerUniqueId = viewerUniqueId;
            this.view = view;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
