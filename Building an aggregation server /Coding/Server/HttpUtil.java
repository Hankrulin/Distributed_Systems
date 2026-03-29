import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class HttpUtil {

    public record Response(int status, String reason, Map<String,String> headers, String body) {}

    public static Response httpGet(String baseUrl, String path, String query, long lamportTs) throws IOException {
        Host h = parseBaseUrl(baseUrl);
        String fullPath = path + (query == null || query.isBlank() ? "" : "?" + query);
        try (Socket sock = new Socket(h.host, h.port)) {
            OutputStream out = sock.getOutputStream();
            InputStream in = sock.getInputStream();

            String req = "GET " + fullPath + " HTTP/1.1\r\n" +
                    "Host: " + h.host + ":" + h.port + "\r\n" +
                    "Connection: close\r\n" +
                    "X-Lamport-TS: " + lamportTs + "\r\n" +
                    "\r\n";
            out.write(req.getBytes(StandardCharsets.UTF_8));
            out.flush();

            return readResponse(in);
        }
    }

    public static Response httpPut(String baseUrl, String path, String json, long lamportTs) throws IOException {
        Host h = parseBaseUrl(baseUrl);
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        try (Socket sock = new Socket(h.host, h.port)) {
            OutputStream out = sock.getOutputStream();
            InputStream in = sock.getInputStream();

            StringBuilder sb = new StringBuilder();
            sb.append("PUT ").append(path).append(" HTTP/1.1\r\n");
            sb.append("Host: ").append(h.host).append(":").append(h.port).append("\r\n");
            sb.append("Connection: close\r\n");
            sb.append("Content-Type: application/json\r\n");
            sb.append("Content-Length: ").append(body.length).append("\r\n");
            sb.append("X-Lamport-TS: ").append(lamportTs).append("\r\n");
            sb.append("\r\n");

            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            out.write(body);
            out.flush();

            return readResponse(in);
        }
    }

    /* ---------------- internal helpers ---------------- */

    private static Response readResponse(InputStream in) throws IOException {
        BufferedInputStream bin = new BufferedInputStream(in);
        String statusLine = readLine(bin);
        if (statusLine == null || statusLine.isEmpty()) throw new IOException("Empty response");
        String[] parts = statusLine.split(" ", 3);
        int status = (parts.length > 1) ? Integer.parseInt(parts[1]) : -1;
        String reason = (parts.length > 2) ? parts[2] : "";

        Map<String,String> headers = new LinkedHashMap<>();
        String line;
        while ((line = readLine(bin)) != null && !line.isEmpty()) {
            int idx = line.indexOf(':');
            if (idx > 0) {
                String k = line.substring(0, idx).trim();
                String v = line.substring(idx + 1).trim();
                headers.put(k, v);
            }
        }
        int len = 0;
        if (headers.containsKey("Content-Length")) {
            try { len = Integer.parseInt(headers.get("Content-Length")); } catch (NumberFormatException ignore) {}
        }
        byte[] body = new byte[len];
        int off = 0;
        while (off < len) {
            int r = bin.read(body, off, len - off);
            if (r == -1) break;
            off += r;
        }
        String bodyStr = new String(body, 0, off, StandardCharsets.UTF_8);
        return new Response(status, reason, headers, bodyStr);
    }

    private static String readLine(BufferedInputStream bin) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int prev = -1, cur;
        while ((cur = bin.read()) != -1) {
            if (prev == '\r' && cur == '\n') break;
            if (prev != -1) buf.write(prev);
            prev = cur;
        }
        if (prev == '\r') { /* eat CR */ }
        else if (prev != -1 && cur == -1) buf.write(prev);
        return buf.toString(StandardCharsets.UTF_8);
    }

    private record Host(String host, int port) {}
    private static Host parseBaseUrl(String baseUrl) {
        // Accept: "localhost:4567" or "http://localhost:4567" or "http://localhost:4567/path"
        String s = baseUrl.trim();
        if (s.startsWith("http://")) s = s.substring(7);
        int slash = s.indexOf('/');
        if (slash >= 0) s = s.substring(0, slash);
        String host = s;
        int port = 80;
        int colon = s.lastIndexOf(':');
        if (colon >= 0) {
            host = s.substring(0, colon);
            port = Integer.parseInt(s.substring(colon + 1));
        }
        return new Host(host, port);
    }
}
