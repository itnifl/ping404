package no.ntnu.ping404.server.game;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe input queue implementing the Producer-Consumer pattern.
 * Required by issue #14 for decoupling network I/O from game logic.
 *
 * Producer: Network thread calls {@link #enqueue(InputEvent)} on packet arrival.
 * Consumer: Game loop thread calls {@link #drainAll()} once per tick.
 *
 * Uses {@link ConcurrentLinkedQueue} for lock-free thread safety.
 */
public class InputQueue {

    private final ConcurrentLinkedQueue<InputEvent> queue;

    public InputQueue() {
        this.queue = new ConcurrentLinkedQueue<>();
    }

    /**
     * Called by the network thread (producer).
     * Thread-safe - can be called concurrently from any thread.
     */
    public void enqueue(InputEvent event) {
        queue.offer(event);
    }

    /**
     * Called by the game loop thread (consumer) once per tick.
     * Atomically drains all pending events into a list for sequential processing.
     * This guarantees a consistent snapshot of inputs per tick.
     */
    public List<InputEvent> drainAll() {
        List<InputEvent> events = new ArrayList<>();
        InputEvent event;
        while ((event = queue.poll()) != null) {
            events.add(event);
        }
        return events;
    }

    /** Number of pending events (for monitoring/debugging). */
    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public void clear() {
        queue.clear();
    }
}
