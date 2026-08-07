package cn.mythicland.lib.container;

/**
 * Owns one client-side container animation session.
 */
public interface ContainerAnimationHandle extends AutoCloseable {

    /**
     * Closes this animation session. The operation is idempotent.
     */
    @Override
    void close();
}
