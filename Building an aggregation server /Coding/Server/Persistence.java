import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Objects;
import java.util.function.Supplier;
import static java.nio.file.StandardOpenOption.*;

/**
 * Persistence & Recovery
 * - WAL (NDJSON per entry) -> fsync
 * - commitAndReturnCreated: atomic replace aggregate.json, return whether it was "first creation"
 * - recover on boot: base=aggregate.json + replay WAL -> atomic write -> truncate WAL
 *
 * Thread-safety:
 * - All public methods are synchronized (file-level). Callers should still use external read/write locks.
 */
public class Persistence {

    private final Path dataFile;
    private final Path walFile;

    public Persistence(String dataFile, String walFile) {
        this.dataFile = Paths.get(Objects.requireNonNull(dataFile));
        this.walFile  = Paths.get(Objects.requireNonNull(walFile));
    }

    /** Recover committed snapshot by replaying WAL onto existing aggregate.json (if any). */
    public synchronized void recover() throws IOException {
        ensureParentDirs();

        String base = "{}";
        if (Files.exists(dataFile)) {
            base = Files.readString(dataFile, StandardCharsets.UTF_8);
        }

        if (!Files.exists(walFile) || Files.size(walFile) == 0L) {
            // nothing to replay; ensure data file exists at least
            if (!Files.exists(dataFile)) {
                atomicWrite(dataFile, base);
            }
            return;
        }

        // Replay WAL line by line (NDJSON: each line an entry JSON)
        String current = base;
        try (BufferedReader br = Files.newBufferedReader(walFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                current = JsonCodec.merge(current, line); // merge one entry
            }
        }

        // Write the recovered snapshot atomically
        atomicWrite(dataFile, current);

        // Truncate/rotate WAL after successful recovery
        truncateWal();
    }

    /** Append one entry (payload JSON) into WAL and fsync. */
    public synchronized void walAppend(String entryJson) throws IOException {
        ensureParentDirs();
        if (!Files.exists(walFile)) {
            Files.createFile(walFile);
        }
        try (FileChannel ch = FileChannel.open(walFile, WRITE, APPEND)) {
            byte[] bytes = (entryJson + "\n").getBytes(StandardCharsets.UTF_8);
            ch.write(java.nio.ByteBuffer.wrap(bytes));
            ch.force(true); // fsync WAL
        }
    }

    /**
     * Commit a new aggregate snapshot and return whether this is the "first creation".
     * "First creation" is defined as:
     *  - data file does not exist, or
     *  - data file exists but is empty/blank, or equals "{}" (ignoring spaces/newlines).
     */
    public synchronized boolean commitAndReturnCreated(Supplier<String> newAggregateSupplier) throws IOException {
        ensureParentDirs();
        boolean created = isFirstCreationCandidate();

        String newAgg = Objects.requireNonNull(newAggregateSupplier.get(), "new aggregate cannot be null");
        atomicWrite(dataFile, newAgg);
        truncateWal();
        return created;
    }

    /** Backward-compatible signature (ignores created flag). */
    public synchronized void commit(Supplier<String> newAggregateSupplier) throws IOException {
        commitAndReturnCreated(newAggregateSupplier);
    }

    /** Read the committed aggregate; if stationId != null, filter via JsonCodec. */
    public synchronized String readCommitted(String stationId) {
        try {
            if (!Files.exists(dataFile)) return "{}";
            String agg = Files.readString(dataFile, StandardCharsets.UTF_8);
            if (stationId == null || stationId.isBlank()) return agg;
            return JsonCodec.filterByStation(agg, stationId);
        } catch (IOException e) {
            // Minimal safe fallback; upper layer may decide to 500
            return "{}";
        }
    }

    /* ============================ helpers ============================ */

    private void ensureParentDirs() throws IOException {
        Path parent = dataFile.getParent();
        if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);
        parent = walFile.getParent();
        if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);
    }

    /** Atomic write of a text file with fsync. */
    private void atomicWrite(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        // write tmp
        try (BufferedWriter bw = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8, CREATE, TRUNCATE_EXISTING, WRITE)) {
            bw.write(content);
            bw.flush();
        }
        // fsync tmp
        try (FileChannel ch = FileChannel.open(tmp, WRITE)) {
            ch.force(true);
        }
        // atomic move
        Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

        // fsync directory (extra safety on some FS)
        Path dir = target.getParent();
        if (dir != null) {
            try (FileChannel dirCh = FileChannel.open(dir, WRITE)) {
                dirCh.force(true);
            } catch (Exception ignore) { /* some FS do not support opening directories */ }
        }
    }

    private void truncateWal() throws IOException {
        if (Files.exists(walFile)) {
            try (FileChannel ch = FileChannel.open(walFile, WRITE)) {
                ch.truncate(0);
                ch.force(true);
            }
        }
    }

    /** Decide whether the upcoming commit should be treated as "first creation" (→ 201). */
    private boolean isFirstCreationCandidate() throws IOException {
        if (!Files.exists(dataFile)) return true;
        long size = Files.size(dataFile);
        if (size == 0L) return true;
        String text = Files.readString(dataFile, StandardCharsets.UTF_8).trim();
        return text.isEmpty() || "{}".equals(text);
    }

    /** Check if WAL has any uncommitted entries pending (to detect in-flight PUT operations). */
    public synchronized boolean hasUncommittedData() {
        try {
            return Files.exists(walFile) && Files.size(walFile) > 0;
        } catch (IOException e) {
            // If unable to determine, assume there is pending data to be safe
            return true;
        }
    }
}
