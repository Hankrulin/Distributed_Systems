import java.util.HashMap;
import java.util.Map;

/**
 * Minimal HTTP request parser + early router.
 *
 * Supports:
 *  - GET /weather.json[?sid=...]
 *  - PUT /weather.json (body: JSON object OR array of objects, each must have string "id")
 *
 * Decisions:
 *  - GET -> Decision.GET
 *  - PUT (empty body) -> Decision.NO_CONTENT (204)
 *  - PUT (bad JSON or missing id) -> Decision.SERVER_ERROR (500)
 *  - Any other method/path -> Decision.BAD_REQUEST (400)
 *
 * Notes:
 *  - "Sending no content to the server" (i.e., PUT with Content-Length: 0 or empty body) should yield 204.
 *  - GET never returns NO_CONTENT here; upstream will always treat it as a normal GET.
 */
public class RequestRouter {

    public ParsedRequest parse(String rawHttp) {
        if (rawHttp == null || rawHttp.isBlank()) {
            return new ParsedRequest(Decision.BAD_REQUEST, null, null,
                    Map.of(), "", 0L, null, null);
        }

        // Split head/body at CRLFCRLF
        String[] parts = rawHttp.split("\r\n\r\n", 2);
        String head = parts[0];
        String body = (parts.length > 1) ? parts[1] : "";

        String[] lines = head.split("\r\n");
        if (lines.length < 1) {
            return new ParsedRequest(Decision.BAD_REQUEST, null, null,
                    Map.of(), "", 0L, null, null);
        }

        // --- Request line ---
        String[] reqLineParts = lines[0].split(" ");
        if (reqLineParts.length < 2) {
            return new ParsedRequest(Decision.BAD_REQUEST, null, null,
                    Map.of(), "", 0L, null, null);
        }
        String method = reqLineParts[0].trim().toUpperCase();
        String path   = reqLineParts[1].trim();

        // --- Headers ---
        Map<String,String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int idx = line.indexOf(':');
            if (idx > 0) {
                String k = line.substring(0, idx).trim();
                String v = line.substring(idx + 1).trim();
                headers.put(k, v);
            }
        }

        // --- Lamport TS (optional) ---
        long lamportTs = 0L;
        String tsHdr = headers.get("X-Lamport-TS");
        if (tsHdr != null) {
            try { lamportTs = Long.parseLong(tsHdr); } catch (NumberFormatException ignore) {}
        }

        // --- Sender ID (only meaningful for PUT) ---
        String senderIdHdr = headers.getOrDefault("X-Sender-ID", "content-server");

        // --- Query (?sid=...) ---
        String stationId = null;
        if (path.contains("?")) {
            String[] pathParts = path.split("\\?", 2);
            path = pathParts[0];
            String query = pathParts.length > 1 ? pathParts[1] : "";
            for (String kv : query.split("&")) {
                String[] kvParts = kv.split("=", 2);
                if (kvParts.length == 2 && kvParts[0].equals("sid")) {
                    stationId = kvParts[1];
                }
            }
        }

        // --- Only /weather.json is supported ---
        if (!"/weather.json".equals(path)) {
            return new ParsedRequest(Decision.BAD_REQUEST, method, path,
                    headers, body, lamportTs, stationId, null);
        }

        // --- Methods ---
        if ("GET".equals(method)) {
            // Sender for GET is not used; keep a fixed label for clarity.
            return new ParsedRequest(Decision.GET, method, path,
                    headers, "", lamportTs, stationId, "client");
        }

        if ("PUT".equals(method)) {
            // "No content" if Content-Length is 0 or body is blank.
            long contentLength = -1L;
            String cl = headers.get("Content-Length");
            if (cl != null) {
                try { contentLength = Long.parseLong(cl); } catch (NumberFormatException ignore) {}
            }
            if (contentLength == 0 || body == null || body.isBlank()) {
                return new ParsedRequest(Decision.NO_CONTENT, method, path,
                        headers, "", lamportTs, stationId, senderIdHdr);
            }

            // Strict payload validation: must be object or array of objects; each must contain string "id".
            try {
                JsonCodec.validatePayload(body.trim());
            } catch (JsonCodec.JsonError e) {
                return new ParsedRequest(Decision.SERVER_ERROR, method, path,
                        headers, body, lamportTs, stationId, senderIdHdr);
            }

            return new ParsedRequest(Decision.PUT, method, path,
                    headers, body, lamportTs, stationId, senderIdHdr);
        }

        // Any other method -> 400
        return new ParsedRequest(Decision.BAD_REQUEST, method, path,
                headers, body, lamportTs, stationId, null);
    }

    // --- DTOs ---

    public enum Decision { GET, PUT, NO_CONTENT, BAD_REQUEST, SERVER_ERROR }

    public record ParsedRequest(
            Decision decision,
            String method,
            String path,
            Map<String,String> headers,
            String payload,
            long lamportTs,
            String stationId,
            String senderId
    ) {}
}

