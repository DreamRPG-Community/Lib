package cn.mythicland.lib.bootstrap.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an injected component that should be registered through Bukkit's ServicesManager.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ServiceComponent {

    /**
     * Returns the service contract implemented by the component.
     *
     * @return service contract type
     */
    Class<?> value();
}
