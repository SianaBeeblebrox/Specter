package io.github.sianabeeblebrox.specter;

import groovy.lang.Closure;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Function;

/**
 * A thread-safe event bus system, higher priority listeners (smaller integer value) are run first (listeners of the
 * same priority are run in the order they were added); if an event listener returns a non-null value, that value is
 * returned to the event dispatcher and stops further execution of listeners
 */
public final class EventBus {
    public static final int HIGH_PRIORITY = -1, DEFAULT_PRIORITY = 0, LOW_PRIORITY = 1;

    /**
     * A handle returned when registering an event listener to allow for its removal
     */
    @FunctionalInterface
    public interface EventListenerHandle {
        /**
         * Removes the event listener (ignores repeated calls)
         */
        void remove();
    }

    private final ConcurrentHashMap<String, ConcurrentSkipListMap<Integer, LinkedBlockingDeque<Closure<Object>>>> EVENT_LISTENERS = new ConcurrentHashMap<>();

    /**
     * Adds an event listener
     * @param event the event name to listen for
     * @param listener the event listener callback
     * @return a {@link io.github.sianabeeblebrox.specter.EventBus.EventListenerHandle} for {@code listener}
     */
    public @Nonnull EventListenerHandle on(final @Nonnull String event, final @Nonnull Function<Object[], Object> listener) {
        return this.on(event, listener, EventBus.DEFAULT_PRIORITY);
    }

    /**
     * Adds an event listener
     * @param event the event name to listen for
     * @param listener the event listener callback
     * @return a {@link io.github.sianabeeblebrox.specter.EventBus.EventListenerHandle} for {@code listener}
    */
    public @Nonnull EventListenerHandle on(final @Nonnull String event, final @Nonnull Closure<Object> listener) {
        return this.on(event, listener, EventBus.DEFAULT_PRIORITY);
    }

    /**
     * Adds an event listener
     * @param event the event name to listen for
     * @param listener the event listener callback
     * @param priority the priority for the event (smaller being higher priority)
     * @return a {@link io.github.sianabeeblebrox.specter.EventBus.EventListenerHandle} for {@code listener}
     */
    public @Nonnull EventListenerHandle on(final @Nonnull String event, final @Nonnull Function<Object[], Object> listener, final int priority) {
        Objects.requireNonNull(listener);
        return this.on(event, new Closure<Object>(null) {
            public Object doCall(final Object... args) {
                return listener.apply(args);
            }
        }, priority);
    }

    /**
     * Adds an event listener
     * @param event the event name to listen for
     * @param listener the event listener callback
     * @param priority the priority for the event (smaller being higher priority)
     * @return a {@link io.github.sianabeeblebrox.specter.EventBus.EventListenerHandle} for {@code listener}
     */
    public @Nonnull EventListenerHandle on(final @Nonnull String event, final @Nonnull Closure<Object> listener, final int priority) {
        final LinkedBlockingDeque<Closure<Object>> lbd = this.EVENT_LISTENERS.computeIfAbsent(Objects.requireNonNull(event), k -> new ConcurrentSkipListMap<>()).computeIfAbsent(priority, k -> new LinkedBlockingDeque<>());
        lbd.add(Objects.requireNonNull(listener));
        return () -> lbd.remove(listener);
    }

    /**
     * Dispatches an event to listeners
     * @param event the event name
     * @param args arguments to pass to the listeners
     * @return the first non-null value returned by event listeners (according to priority) or {@code null} iff none
     */
    public @Nullable Object dispatch(final @Nonnull String event, final @Nullable Object... args) {
        return Optional.ofNullable(this.EVENT_LISTENERS.get(Objects.requireNonNull(event))).flatMap(
                map -> map.values().stream().flatMap(Collection::stream).map(
                        listener -> listener.call(args)
                ).filter(Objects::nonNull).findFirst()
        ).orElse(null);
    }
}
