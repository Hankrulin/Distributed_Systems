import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Test204 {
    public static void main(String[] args) throws Exception {
        // 依需要替換 host/port/sid，建議用一個「一定不存在」的 sid 來觸發 204
        String url = "http://localhost:4567/weather.json?sid=__no_such_sid__";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("X-Lamport-TS", "1")   // 給伺服器 Lamport onReceive 用
                .GET()
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

        System.out.println("Status: " + res.statusCode());
        System.out.println("X-Lamport-TS: " + res.headers().firstValue("X-Lamport-TS").orElse("<missing>"));

        if (res.statusCode() == 204) {
            System.out.println("PASS: got 204 No Content as expected.");
        } else {
            System.out.println("Body (for debugging): " + res.body());
        }
    }
}
