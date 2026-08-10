package cn.mythicland.lib.text;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import eu.decentsoftware.holograms.api.holograms.HologramLine;
import eu.decentsoftware.holograms.api.holograms.HologramPage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * DecentHolograms-backed client-side floating text for the shared Lib runtime.
 *
 * <p>Every display is created with {@code saveToFile=false}. DecentHolograms renders the lines as
 * packets and keeps the fake armor stands out of the world. Text updates use the existing
 * {@link HologramLine} renderer, so a countdown does not destroy and respawn the lines.</p>
 */
public final class DecentFloatingTextService implements FloatingTextService, Listener {

    private static final String DECENT_HOLOGRAMS = "DecentHolograms";

    private final JavaPlugin owner;
    private final Map<UUID, DecentFloatingTextSession> sessions = new HashMap<>();
    private BukkitTask refreshTask;
    private boolean closed;

    /**
     * Creates the shared DecentHolograms floating-text service.
     *
     * @param owner Lib plugin owning the listener, task, and cleanup lifecycle
     */
    public DecentFloatingTextService(JavaPlugin owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
        owner.getServer().getPluginManager().registerEvents(this, owner);
    }

    private static void ensurePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Floating text must be shown on the Bukkit primary thread");
        }
    }

    private static int displayRange(double viewDistance) {
        double rounded = Math.ceil(viewDistance);
        return Math.clamp((int) rounded, 1, Integer.MAX_VALUE);
    }

    @Override
    public void verifyCompatibility() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(DECENT_HOLOGRAMS);
        if (plugin == null || !plugin.isEnabled()) {
            throw new IllegalStateException(
                    "DecentHolograms is required for Lib floating text and must be enabled"
            );
        }
    }

    @Override
    public FloatingTextHandle show(Location location, FloatingTextSpec specification) {
        ensurePrimaryThread();
        if (closed) throw new IllegalStateException("Floating text service is closed");
        verifyCompatibility();
        Location anchor = Objects.requireNonNull(location, "location").clone();
        if (anchor.getWorld() == null) throw new IllegalArgumentException("location.world cannot be null");
        FloatingTextSpec value = Objects.requireNonNull(specification, "specification");
        DecentFloatingTextSession session = new DecentFloatingTextSession(this, anchor, value);
        sessions.put(session.id(), session);
        try {
            ensureRefreshTask();
            session.initialize();
            session.refreshViewers();
            return session;
        } catch (RuntimeException exception) {
            session.closeInternal();
            throw exception;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        hideFrom(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        hideFrom(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!event.isCancelled()) hideFrom(event.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        ensurePrimaryThread();
        if (closed) return;
        closed = true;
        if (refreshTask != null) refreshTask.cancel();
        refreshTask = null;
        for (DecentFloatingTextSession session : List.copyOf(sessions.values())) session.closeInternal();
        sessions.clear();
    }

    private void ensureRefreshTask() {
        if (refreshTask != null) return;
        refreshTask = owner.getServer().getScheduler().runTaskTimer(owner, this::refreshAll, 1L, 10L);
    }

    private void refreshAll() {
        if (closed) return;
        for (DecentFloatingTextSession session : List.copyOf(sessions.values())) session.refreshViewers();
    }

    private void hideFrom(UUID playerUniqueId) {
        for (DecentFloatingTextSession session : List.copyOf(sessions.values())) {
            session.hideFrom(playerUniqueId);
        }
    }

    private void remove(DecentFloatingTextSession session) {
        sessions.remove(session.id(), session);
        if (sessions.isEmpty() && refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    private static final class DecentFloatingTextSession implements FloatingTextHandle {

        private final DecentFloatingTextService service;
        private final UUID id = UUID.randomUUID();
        private final Set<UUID> visibleTo = new HashSet<>();
        private Location anchor;
        private FloatingTextSpec specification;
        private Hologram hologram;
        private HologramPage page;
        private List<HologramLine> lines = List.of();
        private boolean closed;

        private DecentFloatingTextSession(
                DecentFloatingTextService service,
                Location anchor,
                FloatingTextSpec specification
        ) {
            this.service = service;
            this.anchor = anchor;
            this.specification = specification;
        }

        private UUID id() {
            return id;
        }

        private void initialize() {
            String name = "lib_" + id.toString().replace("-", "");
            Hologram created = new Hologram(name, anchor, false);
            try {
                hologram = created;
                page = created.getPage(0);
                configureHologram(specification);
                replaceLines(specification.lines(), specification.lineSpacing());
                created.setDefaultVisibleState(false);
            } catch (RuntimeException exception) {
                created.delete();
                hologram = null;
                page = null;
                throw exception;
            }
        }

        @Override
        public void update(FloatingTextSpec specification) {
            ensurePrimaryThread();
            if (closed) return;
            FloatingTextSpec next = Objects.requireNonNull(specification, "specification");
            boolean lineCountChanged = lines.size() != next.lines().size();
            boolean spacingChanged = Double.compare(this.specification.lineSpacing(), next.lineSpacing()) != 0;
            if (lineCountChanged) hideVisibleViewers();
            this.specification = next;
            configureHologram(next);
            if (lineCountChanged) {
                replaceLines(next.lines(), next.lineSpacing());
            } else {
                if (spacingChanged) moveLines(next.lineSpacing());
                for (int index = 0; index < lines.size(); index++) {
                    String nextText = next.lines().get(index);
                    if (!nextText.equals(lines.get(index).getContent())) {
                        DHAPI.setHologramLine(lines.get(index), nextText);
                    }
                }
            }
            refreshViewers();
        }

        @Override
        public void move(Location location) {
            ensurePrimaryThread();
            if (closed) return;
            Location next = Objects.requireNonNull(location, "location").clone();
            if (next.getWorld() == null) throw new IllegalArgumentException("location.world cannot be null");
            if (anchor.getWorld() == null
                    || !anchor.getWorld().getUID().equals(next.getWorld().getUID())) {
                hideVisibleViewers();
            }
            anchor = next;
            hologram.setLocation(next);
            moveLines(specification.lineSpacing());
            refreshViewers();
        }

        @Override
        public void close() {
            ensurePrimaryThread();
            closeInternal();
        }

        private void configureHologram(FloatingTextSpec value) {
            int range = displayRange(value.viewDistance());
            hologram.setDisplayRange(range);
            hologram.setUpdateRange(range);
            // Countdown updates are driven explicitly by FloatingTextHandle.update().
            hologram.setUpdateInterval(Integer.MAX_VALUE);
        }

        private void replaceLines(List<String> textLines, double lineSpacing) {
            for (int index = page.size() - 1; index >= 0; index--) page.removeLine(index);
            List<HologramLine> replacement = new ArrayList<>(textLines.size());
            for (String text : textLines) {
                HologramLine line = new HologramLine(page, page.getNextLineLocation(), text);
                if (!page.addLine(line)) throw new IllegalStateException("DecentHolograms rejected a text line");
                replacement.add(line);
            }
            for (int index = 0; index < replacement.size(); index++) {
                replacement.get(index).setLocation(lineLocation(index, lineSpacing));
            }
            lines = List.copyOf(replacement);
        }

        private void moveLines(double lineSpacing) {
            for (int index = 0; index < lines.size(); index++) {
                HologramLine line = lines.get(index);
                line.setLocation(lineLocation(index, lineSpacing));
                line.updateLocation(true);
            }
        }

        private Location lineLocation(int index, double lineSpacing) {
            return anchor.clone().subtract(0.0D, index * lineSpacing, 0.0D);
        }

        private void refreshViewers() {
            if (closed || hologram == null) return;
            World world = anchor.getWorld();
            if (world == null) return;
            double maxDistanceSquared = specification.viewDistance() * specification.viewDistance();
            Set<UUID> seen = new HashSet<>();
            for (Player player : world.getPlayers()) {
                if (!player.isOnline()) continue;
                if (player.getLocation().distanceSquared(anchor) > maxDistanceSquared) continue;
                UUID playerUniqueId = player.getUniqueId();
                seen.add(playerUniqueId);
                if (!visibleTo.contains(playerUniqueId)) {
                    // DecentHolograms returns false when default visibility is disabled unless
                    // this explicit viewer override is registered first.
                    hologram.setShowPlayer(player);
                    if (hologram.show(player, 0)) visibleTo.add(playerUniqueId);
                }
            }
            for (UUID playerUniqueId : List.copyOf(visibleTo)) {
                if (!seen.contains(playerUniqueId)) hideFrom(playerUniqueId);
            }
        }

        private void hideVisibleViewers() {
            for (UUID playerUniqueId : List.copyOf(visibleTo)) hideFrom(playerUniqueId);
        }

        private void hideFrom(UUID playerUniqueId) {
            visibleTo.remove(playerUniqueId);
            Player player = Bukkit.getPlayer(playerUniqueId);
            if (player != null && player.isOnline() && hologram != null) {
                hologram.hide(player);
                hologram.removeShowPlayer(player);
            }
        }

        private void closeInternal() {
            if (closed) return;
            closed = true;
            hideVisibleViewers();
            visibleTo.clear();
            if (hologram != null) hologram.delete();
            hologram = null;
            page = null;
            lines = List.of();
            service.remove(this);
        }
    }
}
