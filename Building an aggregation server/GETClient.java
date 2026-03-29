public class GETClient {
    public static void main(String[] args) throws Exception {
        String url = null;
        String sid = null;
        for (int i = 0; i < args.length; i++) {
            if ("-url".equals(args[i]) && i + 1 < args.length) url = args[++i];
            else if ("-sid".equals(args[i]) && i + 1 < args.length) sid = args[++i];
        }
        if (url == null) {
            System.err.println("Usage: java GETClient -url host:port [-sid STATION_ID]");
            System.exit(2);
        }

        LamportClock lc = new LamportClock();
        long ts = lc.onSend();
        String query = (sid == null) ? null : ("sid=" + sid);
        HttpUtil.Response resp = HttpUtil.httpGet(url, "/weather.json", query, ts);

        // Lamport: update on receive if header present
        String tsHdr = resp.headers().getOrDefault("X-Lamport-TS", "0");
        try { lc.onReceive(Long.parseLong(tsHdr)); } catch (NumberFormatException ignore) {}

        System.out.println("GET status: " + resp.status() + " " + resp.reason());
        System.out.println("X-Lamport-TS (resp): " + tsHdr);
        if (resp.body() != null && !resp.body().isBlank()) {
            System.out.println(resp.body());
        }
    }
}
