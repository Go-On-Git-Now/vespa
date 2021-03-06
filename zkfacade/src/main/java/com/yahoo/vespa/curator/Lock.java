// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.yahoo.transaction.Mutex;
import org.apache.curator.framework.recipes.locks.InterProcessLock;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * A cluster-wide re-entrant mutex which is released on (the last symmetric) close
 *
 * @author bratseth
 */
public class Lock implements Mutex {

    private final InterProcessLock mutex;
    private final String lockPath;

    public Lock(String lockPath, Curator curator) {
        this.lockPath = lockPath;
        mutex = curator.createMutex(lockPath);
    }

    /** Take the lock with the given timeout. This may be called multiple times from the same thread - each matched by a close */
    public void acquire(Duration timeout) throws UncheckedTimeoutException {
        boolean acquired;
        try {
            acquired = mutex.acquire(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (Exception e) {
            throw new RuntimeException("Exception acquiring lock '" + lockPath + "'", e);
        }

        if (! acquired)
            throw new UncheckedTimeoutException("Timed out after waiting " + timeout +
                                                " to acquire lock '" + lockPath + "'");
    }

    @Override
    public void close() {
        try {
            mutex.release();
        }
        catch (Exception e) {
            throw new RuntimeException("Exception releasing lock '" + lockPath + "'");
        }
    }

}


