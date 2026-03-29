import java.util.*;  // for List, Map, etc.
import java.io.IOException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.*;
import java.util.Comparator;

/**
 * Concurrency control for Aggregation Server.
 * - Provides read/write critical sections (ReentrantReadWriteLock).
 * - Serializes PUT operations by Lamport timestamp using a priority queue and a single worker.
 */
public class ConcurrencyControl {

    private final ReadWriteLock rw = new ReentrantReadWriteLock();
    private final PriorityBlockingQueue<PutTask> putQueue =
            new PriorityBlockingQueue<>(64, Comparator.comparingLong(PutTask::lamportTs));
    private final ExecutorService putWorker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "put-worker");
        t.setDaemon(true);
        return t;
    });

    public ConcurrencyControl(LamportClock lc) {
        // Start a worker that drains the queue and executes tasks in Lamport order
        putWorker.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    PutTask task = putQueue.take();
                    task.run(lc);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        });
    }

    /** Execute a read function under read lock. */
    public <T> T withRead(Callable<T> fn) {
        rw.readLock().lock();
        try {
            return fn.call();
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            rw.readLock().unlock();
        }
    }

    /** Execute a write function under write lock. */
    public <T> T withWrite(Callable<T> fn) {
        rw.writeLock().lock();
        try {
            return fn.call();
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            rw.writeLock().unlock();
        }
    }

    /** Submit a PUT task to be executed in Lamport order. */
    public void submitPut(PutTask task) {
        putQueue.add(task);
    }

    /** Graceful shutdown (optional). */
    public void shutdown() {
        putWorker.shutdownNow();
    }

    /* ========================== Tasks & DTOs ========================== */

    @FunctionalInterface
    public interface Responder {
        void reply(int statusCode, boolean created, long lamportTsOut);
    }

    @FunctionalInterface
    public interface SupplierE<T> { T get(); } // simple supplier (no checked exception)

    public static class PutTask {
        private final long lamportTs;          // remote Lamport timestamp from request
        private final String senderId;         // content server identifier (or "content-server")
        private final String payload;          // JSON body (object or array of objects)
        private final Responder responder;     // callback to write HTTP response
        private final Runnable onReceiveHook;  // typically: () -> lc.onReceive(lamportTs)
        private final SupplierE<Persistence> persistence;
        private final SupplierE<ConcurrencyControl> control;

        public PutTask(long lamportTs,
                       String senderId,
                       String payload,
                       Responder responder,
                       Runnable onReceiveHook,
                       SupplierE<Persistence> persistence,
                       SupplierE<ConcurrencyControl> control) {
            this.lamportTs = lamportTs;
            this.senderId = senderId;
            this.payload = payload;
            this.responder = responder;
            this.onReceiveHook = onReceiveHook;
            this.persistence = persistence;
            this.control = control;
        }

        public long lamportTs() { return lamportTs; }

        /** Main execution of the PUT under Lamport ordering + WAL + write-lock discipline. */
        public void run(LamportClock lc) {
            try {
                // (0) Synchronize Lamport clock with incoming request
                onReceiveHook.run();

                Persistence p = persistence.get();
                // (1) Append to WAL (with fsync) for crash-safety
                p.walAppend(payload);

                // (2) Inside write lock: read current snapshot → merge → commit atomically
                boolean created = control.get().withWrite(() -> {
                    String current = p.readCommitted(null);
                    String newAgg = JsonCodec.merge(current, payload);
                    // Determine if any new station IDs are introduced by this PUT
                    boolean newData = false;
                    List<String> ids = JsonCodec.extractIds(payload);
                    for (String id : ids) {
                        if (!AggregationServer.knownIds.contains(id)) {
                            newData = true;
                        }
                    }
                    // Commit new aggregate snapshot (WAL will be cleared on success)
                    boolean createdFlag = p.commitAndReturnCreated(() -> newAgg);
                    // Update in-memory tracking for all affected station IDs
                    long now = System.currentTimeMillis();
                    for (String id : ids) {
                        AggregationServer.knownIds.add(id);
                        AggregationServer.lastSeen.put(id, now);
                    }
                    // Return true if a new station was created (or first data ever)
                    return createdFlag || newData;
                });

                // (3) Send reply with Lamport timestamp (reply status 201 if created, else 200)
                long tsOut = lc.onSend();
                responder.reply(created ? 201 : 200, created, tsOut);

            } catch (Exception e) {
                e.printStackTrace();
                // Decide between internal server error (500) or service unavailable (503) for failures
                int statusCode;
                if (e instanceof IOException || (e instanceof RuntimeException && e.getCause() instanceof IOException)) {
                    statusCode = 503;  // internal I/O or contention issue
                } else {
                    statusCode = 500;  // other errors (unexpected)
                }
                long tsOut = lc.onSend();
                responder.reply(statusCode, false, tsOut);
            }
        }
    }
}
