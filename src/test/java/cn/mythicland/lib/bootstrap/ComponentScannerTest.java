package cn.mythicland.lib.bootstrap;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the JDK-only scanner can discover compiled Lib component infrastructure.
 */
class ComponentScannerTest {

    @Test
    void scannerFindsClassesFromAnExplodedGradleClasspath() {
        List<Class<?>> classes = ComponentScanner.scan(
                getClass().getClassLoader(),
                "cn.mythicland.lib.bootstrap"
        );

        assertTrue(classes.contains(ComponentContainer.class));
        assertTrue(classes.contains(PluginBootstrap.class));
    }
}
