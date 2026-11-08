package com.linkline.fam.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.SwingUtilities;

/**
 * Tiny publish/subscribe bus. Services publish from any thread; subscribers always run on the
 * Swing event dispatch thread, so UI code can react directly.
 */
public final class EventBus {

    public enum Topic { SCHEDULES, AUDIO, HISTORY, DEVICES, SETTINGS, PLAYBACK, PREVIEW, SCHEDULER }

    private final Map<Topic, List<Runnable>> subscribers = new EnumMap<>(Topic.class);

    public EventBus() {
        for (Topic t : Topic.values()) {
            subscribers.put(t, new CopyOnWriteArrayList<>());
        }
    }

    /** @return a handle that removes the subscription when run */
    public Runnable subscribe(Topic topic, Runnable listener) {
        subscribers.get(topic).add(listener);
        return () -> subscribers.get(topic).remove(listener);
    }

    public void publish(Topic topic) {
        List<Runnable> list = subscribers.get(topic);
        if (list.isEmpty()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            for (Runnable r : list) {
                try {
                    r.run();
                } catch (RuntimeException e) {
                    java.util.logging.Logger.getLogger(EventBus.class.getName())
                            .log(java.util.logging.Level.WARNING, "Listener for " + topic + " failed", e);
                }
            }
        });
    }
}
