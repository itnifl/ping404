package no.creekcode.kryonet.dispatch.client;

/**
 * Strategy for dispatching packet-handler callbacks onto the desired thread.
 *
 * <p>LibGDX callers pass {@code Gdx.app::postRunnable} to execute handlers
 * on the render thread. Headless or test callers pass {@code Runnable::run}
 * for synchronous execution on the calling thread.</p>
 */
@FunctionalInterface
public interface ThreadDispatcher {

    void dispatch(Runnable task);

    /** Executes tasks synchronously on the calling thread. Useful for tests and headless servers. */
    ThreadDispatcher DIRECT = Runnable::run;
}
