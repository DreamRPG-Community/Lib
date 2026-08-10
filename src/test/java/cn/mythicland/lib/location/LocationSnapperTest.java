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

    @Test
    void snapsHorizontalViewWithZeroPitch() {
        World world = testWorld();
        Location source = new Location(world, 4.2D, 9.5D, -3.8D, -67.0F, 61.0F);

        Location snapped = LocationSnapper.snapBlockAndHorizontalView(source);

        assertSame(world, snapped.getWorld());
        assertEquals(4.5D, snapped.getX());
        assertEquals(9.5D, snapped.getY());
        assertEquals(-3.5D, snapped.getZ());
        assertEquals(-45.0F, snapped.getYaw());
        assertEquals(0.0F, snapped.getPitch());
        assertEquals(61.0F, source.getPitch());
    }

    @Test
    void snapsOnlyBlockCenterForStableRelativeDirections() {
        World world = testWorld();
        Location source = new Location(world, -1.2D, 7.25D, 2.8D, 67.0F, -68.0F);

        Location snapped = LocationSnapper.snapBlockCenter(source);

        assertSame(world, snapped.getWorld());
        assertEquals(-1.5D, snapped.getX());
        assertEquals(7.25D, snapped.getY());
        assertEquals(2.5D, snapped.getZ());
        assertEquals(67.0F, snapped.getYaw());
        assertEquals(-68.0F, snapped.getPitch());
        assertEquals(-1.2D, source.getX());
    }

    private static World testWorld() {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "test-world";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == (arguments == null ? null : arguments[0]);
                            default -> throw new UnsupportedOperationException(method.getName());
                        };
                    }
                    return primitiveDefault(method.getReturnType());
                }
        );
    }

    private static Object primitiveDefault(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0F;
        if (type == double.class) return 0.0D;
        return null;
    }
}
