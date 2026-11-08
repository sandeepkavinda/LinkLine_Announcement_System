package com.linkline.fam.ui;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

/** Runs work off the Swing event thread (database, file copies, device calls) and reports back on it. */
public final class Async {

    private static final Logger LOG = Logger.getLogger(Async.class.getName());

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "fam-ui-worker");
        t.setDaemon(true);
        return t;
    });

    private Async() {
    }

    /** Runs {@code work} in the background; {@code onDone} and {@code onError} run on the event thread. */
    public static <T> void run(Callable<T> work, Consumer<T> onDone, Consumer<Throwable> onError) {
        POOL.execute(() -> {
            try {
                T result = work.call();
                SwingUtilities.invokeLater(() -> onDone.accept(result));
            } catch (Throwable e) {
                LOG.log(Level.INFO, "Background task failed", e);
                SwingUtilities.invokeLater(() -> onError.accept(e));
            }
        });
    }

    public static void run(Runnable work, Runnable onDone, Consumer<Throwable> onError) {
        run(() -> {
            work.run();
            return Boolean.TRUE;
        }, ok -> onDone.run(), onError);
    }

    /** Fire and forget, with errors logged only. */
    public static void run(Runnable work) {
        POOL.execute(() -> {
            try {
                work.run();
            } catch (Throwable e) {
                LOG.log(Level.WARNING, "Background task failed", e);
            }
        });
    }
}
