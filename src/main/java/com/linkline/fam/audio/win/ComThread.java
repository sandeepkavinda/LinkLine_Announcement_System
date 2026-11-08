package com.linkline.fam.audio.win;

import com.sun.jna.platform.win32.Ole32;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * All Core Audio calls run on this one thread, which has COM initialised (multithreaded apartment).
 * That keeps COM pointers valid between calls and keeps COM work off the Swing thread.
 */
final class ComThread {

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(worker -> {
        Thread t = new Thread(() -> {
            Ole32.INSTANCE.CoInitializeEx(null, 0x0 /* COINIT_MULTITHREADED */);
            try {
                worker.run();
            } finally {
                Ole32.INSTANCE.CoUninitialize();
            }
        }, "fam-core-audio");
        t.setDaemon(true);
        return t;
    });

    private ComThread() {
    }

    static <T> T call(Callable<T> task) throws Exception {
        Future<T> future = EXEC.submit(task);
        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw e;
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IllegalStateException("Windows audio did not respond within 15 seconds", e);
        }
    }
}
