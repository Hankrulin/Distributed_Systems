public class RouterDemo {
    public static void main(String[] args) {
        String http = "PUT /weather.json HTTP/1.1\r\n" +
                      "Host: localhost\r\n" +
                      "X-Lamport-TS: 5\r\n" +
                      "Content-Length: 20\r\n" +
                      "\r\n" +
                      "{\"id\":\"A\",\"v\":1}";

        RequestRouter rr = new RequestRouter();
        RequestRouter.ParsedRequest req = rr.parse(http);

        System.out.println("Decision=" + req.decision());
        System.out.println("Payload=" + req.payload());
        System.out.println("LamportTS=" + req.lamportTs());
    }
}