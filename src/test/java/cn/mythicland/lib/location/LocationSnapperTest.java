package cn.mythicland.lib.location;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies MythicThePit-compatible administrator location snapping.
 */
class LocationSnapperTest {

    @Test
    void snapsBlockCenterAndViewAnglesWithoutChangingHeight() {
        World world = testWorld();
        Location source = new Location(world, -1.2D, 7.25D, 2.8D, 67.0F, -68.0F);

        Location snapped = LocationSnapper.snapBlockAndView(source);

        assertSame(world, snapped.getWorld());
        assertEquals(-1.5D, snapped.getX());
        assertEquals(7.25D, snapped.getY());
        assertEquals(2.5D, snapped.getZ());
        assertEquals(45.0F, snapped.getYaw());
        assertEquals(-90.0F, snapped.getPitch());
        assertEquals(-1.2D, source.getX());
    }

    @Test
    void rejectsLocationsWithoutAWorld() {
        Location source = new Location(null, 1.0D, 2.0D, 3.0D, 0.0F, 0.0F);

        assertThrows(IllegalArgumentException.class, () -> LocationSnapper.snapBlockAndView(source));
    }

    private static World testWorld() {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, arguments) -> null
        );
    }
}
