public class JsonCodecDemo {
    public static void main(String[] args) {
        // ---- 1) validatePayload testing ----
        String one = "{\"id\":\"A\",\"name\":\"Adelaide\",\"temp\":21.3}";
        String many = "[{\"id\":\"B\",\"temp\":20},{\"id\":\"C\",\"temp\":18.5}]";
        String bad = "{\"name\":\"NoId\"}";

        try {
            JsonCodec.validatePayload(one);
            System.out.println("validate(one): OK");
        } catch (JsonCodec.JsonError e) {
            System.out.println("validate(one) ERROR: " + e.getMessage());
        }

        try {
            JsonCodec.validatePayload(many);
            System.out.println("validate(many): OK");
        } catch (JsonCodec.JsonError e) {
            System.out.println("validate(many) ERROR: " + e.getMessage());
        }

        try {
            JsonCodec.validatePayload(bad);
            System.out.println("validate(bad): OK (unexpected)");
        } catch (JsonCodec.JsonError e) {
            System.out.println("validate(bad): expected error -> " + e.getMessage());
        }

        // 2) merge Single transaction + multiple transactions
        String agg = "{}";
        agg = JsonCodec.merge(agg, one);   // 合併 A
        System.out.println("after merge(one): " + agg);

        agg = JsonCodec.merge(agg, many);  // 再合併 B, C
        System.out.println("after merge(many): " + agg);

        // ---- 3) filterByStation ----
        String onlyB = JsonCodec.filterByStation(agg, "B");
        System.out.println("filter sid=B -> " + onlyB);

        String none = JsonCodec.filterByStation(agg, "ZZZ");
        System.out.println("filter sid=ZZZ -> " + none);
    }
}
