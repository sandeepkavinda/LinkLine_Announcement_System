package com.linkline.fam.platform;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Only one FAM may run per data folder. Two instances would both fire the same announcements, so the
 * second one is refused.
 */
public final class SingleInstance {

    private FileChannel channel;
    private FileLock lock;

    public boolean acquire(Path lockFile) {
        try {
            channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                return false;
            }
            return true;
        } catch (OverlappingFileLockException e) {
            return false;
        } catch (IOException e) {
            // If the lock cannot be created at all, do not block the user from starting FAM.
            return true;
        }
    }

    public void release() {
        try {
            if (lock != null) {
                lock.release();
            }
            if (channel != null) {
                channel.close();
            }
        } catch (IOException ignored) {
            // exiting anyway
        }
    }
}
