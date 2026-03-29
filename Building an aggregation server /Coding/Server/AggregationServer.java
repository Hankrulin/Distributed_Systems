import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * Minimal Aggregation Server:
 * - Starts on a TCP port (default 4567).
 * - On boot: Persistence.recover().
 * - For each connection: read full HTTP, route via RequestRouter.
 * - GET /weather.json[?sid=...] -> return committed aggregate (optionally filtered).
 * - PUT /weather.json -> validated in RequestRouter, ordered & committed via ConcurrencyControl.
 * - Every response includes X-Lamport-TS header.
 *
 * Usage:
 *   java AggregationServer -p 4567
 * 
 * Fields in the aggregation server:
 * 'int port' for port number
 * 'LamportClock' for logical time
 * 'RequestRouter' for reading the request from the HTTP
 * 'Persistence' for committed state & WAL
 * 'ConcurrencyControl' is a concurrency controller to serialize writes
 */
public class AggregationServer {

    private final int port;
    private final LamportClock clock = new LamportClock();
    private final RequestRouter router = new RequestRouter();
    private final Persistence persistence;
    private final ConcurrencyControl cc;

    // Track last update time for each content server (station ID) and active station IDs
    public static final Map<String, Long> lastSeen = new ConcurrentHashMap<>();
    public static final Set<String> knownIds = ConcurrentHashMap.newKeySet();

    // Constructor for the aggregation server
    public AggregationServer(int port, String dataPath, String walPath) throws IOException {
        this.port = port;
        this.persistence = new Persistence(dataPath, walPath);
        this.persistence.recover();
        this.cc = new ConcurrencyControl(clock);
        // Initialize known station IDs from persisted data and mark them as recently seen
        String aggData = this.persistence.readCommitted(null);
        for (String id : JsonCodec.listKeys(aggData)) {
            knownIds.add(id);
            lastSeen.put(id, System.currentTimeMillis());
        }
    }

    // Accept loop, create a new thread for each connection
    public void start() throws IOException {
        try (ServerSocket ss = new ServerSocket(port)) {
            System.out.println("AggregationServer listening on port " + port);
            // Start background thread to remove stale data from inactive content servers
            Thread remover = new Thread(() -> {
                try {
                    while (true) {
                        Thread.sleep(1000);
                        removeExpiredEntries();
                    }
                } catch (InterruptedException e) {
                    // Thread interrupted, exit loop
                }
            }, "remover");
            remover.setDaemon(true);
            remover.start();
            // Main accept loop
            while (true) {
                Socket s = ss.accept();
                new Thread(() -> handle(s), "conn-" + s.getPort()).start();
            }
        }
    }

    private void handle(Socket s) {
        try (Socket sock = s;
             InputStream in = sock.getInputStream();
             OutputStream out = sock.getOutputStream()) {

            // Read full HTTP request and parse it
            String raw = readFullHttpRequest(in);
            RequestRouter.ParsedRequest req = router.parse(raw);

            // Route to appropriate handler or generate error responses
            switch (req.decision()) {
                case GET -> handleGet(req, out);
                case PUT -> handlePut(req, out);
                case NO_CONTENT -> {
                    // No content provided in PUT: return 204 and update last-seen if applicable
                    long ts = clock.onSend();
                    if (req.senderId() != null) {
                        // Update lastSeen timestamp if we can identify the content server
                        if (knownIds.contains(req.senderId())) {
                            lastSeen.put(req.senderId(), System.currentTimeMillis());
                        } else if (knownIds.size() == 1) {
                            // If only one station is known, assume this sender corresponds to it
                            String onlyId = knownIds.iterator().next();
                            lastSeen.put(onlyId, System.currentTimeMillis());
                        }
                    }
                    writeJson(out, 204, "No Content", "", ts, Map.of());
                }
                case SERVER_ERROR -> {
                    // Malformed JSON or missing "id" in PUT: 500 Internal Server Error
                    long ts = clock.onSend();
                    writeJson(out, 500, "Internal Server Error", "", ts, Map.of());
                }
                default -> {
                    // Any other method/path: 400 Bad Request
                    long ts = clock.onSend();
                    writeJson(out, 400, "Bad Request", "", ts, Map.of());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Best-effort: connection may have been closed or other I/O error
        }
    }

    /* ============================ Handlers ============================ */

    private void handleGet(RequestRouter.ParsedRequest req, OutputStream out) throws IOException {
        // Lamport: on receive from client (update logical clock)
        clock.onReceive(req.lamportTs());
        // Read committed snapshot (optionally filter by station id if provided)
        String body = cc.withRead(() -> persistence.readCommitted(req.stationId()));
        long tsOut = clock.onSend();
        if (isNoContent(body)) {
            // No data available → reply 204 with empty body
            writeJson(out, 204, "No Content", "", tsOut, Map.of());
        } else {
            // Data exists → reply 200 OK with JSON payload
            writeJson(out, 200, "OK", body, tsOut, Map.of());
        }
    }

    private static boolean isNoContent(String body) {
        if (body == null) return true;
        String t = body.trim();
        if (t.isEmpty()) return true;
        return "{}".equals(t) || "[]".equals(t);
    }

    private void handlePut(RequestRouter.ParsedRequest req, OutputStream out) throws InterruptedException {
        final CountDownLatch done = new CountDownLatch(1);

        ConcurrencyControl.Responder responder = (status, created, tsOut) -> {
            try {
                // Determine HTTP reason phrase based on status code
                String reason;
                if (status == 201) {
                    reason = "Created";
                } else if (status == 200) {
                    reason = "OK";
                } else if (status == 503) {
                    reason = "Service Unavailable";
                } else {
                    reason = "Internal Server Error";
                }
                // Respond with no body for PUT
                writeJson(out, status, reason, "", tsOut, Map.of("X-Created", String.valueOf(created)));
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                done.countDown();
            }
        };

        // Submit the PUT task to concurrency controller (will execute in order)
        cc.submitPut(new ConcurrencyControl.PutTask(
                req.lamportTs(),
                (req.senderId() == null ? "content-server" : req.senderId()),
                req.payload(),
                responder,
                () -> clock.onReceive(req.lamportTs()),
                () -> persistence,
                () -> cc
        ));

        done.await(); // wait until worker thread commits and sends response
    }

    /* ============================ Background Expiration Task ============================ */

    /**
     * Remove any stations whose content servers have not sent an update within the last 30 seconds.
     * This runs under the write lock to ensure consistency with ongoing PUT operations.
     */
    private void removeExpiredEntries() {
        long now = System.currentTimeMillis();
        List<String> expiredIds = new ArrayList<>();
        for (Map.Entry<String, Long> entry : lastSeen.entrySet()) {
            if (now - entry.getValue() > 30000) {  // 30 seconds threshold
                expiredIds.add(entry.getKey());
            }
        }
        if (expiredIds.isEmpty()) return;
        // Use write lock for atomic removal
        cc.withWrite(() -> {
            // If there are uncommitted WAL entries, skip removal to avoid interfering with in-flight PUT
            if (persistence.hasUncommittedData()) {
                return false;
            }
            try {
                String current = persistence.readCommitted(null);
                String newAgg = JsonCodec.removeEntries(current, expiredIds);
                persistence.commit(() -> newAgg);
            } catch (IOException e) {
                e.printStackTrace();
                // If write to file fails, do not remove from memory
                return false;
            }
            // Update in-memory tracking: remove expired stations
            for (String id : expiredIds) {
                knownIds.remove(id);
                lastSeen.remove(id);
            }
            return true;
        });
    }

    /* ============================ HTTP I/O helpers ============================ */

    private static String readFullHttpRequest(InputStream in) throws IOException {
        // Read headers
        ByteArrayOutputStream headBuf = new ByteArrayOutputStream();
        int state = 0; // detect \r\n\r\n sequence
        while (true) {
            int b = in.read();
            if (b == -1) break;
            headBuf.write(b);
            // State machine to detect end of headers (CRLFCRLF)
            switch (state) {
                case 0 -> state = (b == '\r') ? 1 : 0;
                case 1 -> state = (b == '\n') ? 2 : 0;
                case 2 -> state = (b == '\r') ? 3 : 0;
                case 3 -> {
                    if (b == '\n') {
                        String head = headBuf.toString(StandardCharsets.UTF_8);
                        int cl = parseContentLength(head);
                        byte[] body = new byte[cl];
                        int off = 0;
                        while (off < cl) {
                            int r = in.read(body, off, cl - off);
                            if (r == -1) break;
                            off += r;
                        }
                        return head + new String(body, 0, off, StandardCharsets.UTF_8);
                    } else {
                        state = 0;
                    }
                }
            }
        }
        return headBuf.toString(StandardCharsets.UTF_8);
    }

    private static int parseContentLength(String head) {
        for (String line : head.split("\r\n")) {
            int idx = line.indexOf(':');
            if (idx > 0) {
                String k = line.substring(0, idx).trim();
                String v = line.substring(idx + 1).trim();
                if (k.equalsIgnoreCase("Content-Length")) {
                    try {
                        return Integer.parseInt(v);
                    } catch (NumberFormatException ignore) {}
                }
            }
        }
        return 0;
    }

    private static void writeJson(OutputStream out, int status, String reason, String body, long lamportTs, Map<String, String> extraHeaders) throws IOException {
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n");
        sb.append("Content-Type: application/json\r\n");
        sb.append("Content-Length: ").append(bytes.length).append("\r\n");
        sb.append("Connection: close\r\n");
        sb.append("X-Lamport-TS: ").append(lamportTs).append("\r\n");
        if (extraHeaders != null) {
            for (var e : extraHeaders.entrySet()) {
                sb.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
            }
        }
        sb.append("\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        if (bytes.length > 0) out.write(bytes);
        out.flush();
    }

    /* ============================ Main ============================ */

    // Parse CLI flags, ensure data file exists, construct server, start accept loop.
    public static void main(String[] args) throws Exception {
        int port = 4567;
        String data = "aggregate.json";
        String wal  = "aggregate.wal";

        for (int i = 0; i < args.length; i++) {
            if ("-p".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            } else if ("-data".equals(args[i]) && i + 1 < args.length) {
                data = args[++i];
            } else if ("-wal".equals(args[i]) && i + 1 < args.length) {
                wal = args[++i];
            }
        }

        // Ensure data file exists for first boot (optional)
        if (!Files.exists(Path.of(data))) {
            Files.writeString(Path.of(data), "{}", StandardCharsets.UTF_8);
        }

        AggregationServer server = new AggregationServer(port, data, wal);
        server.start();
    }
}
