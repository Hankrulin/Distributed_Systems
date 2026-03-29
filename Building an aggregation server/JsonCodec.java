import java.util.*;
/**
 * JSON codec with custom parser.
 * Provides:
 *  - JsonError: thrown on invalid JSON or schema errors (e.g., missing "id").
 *  - validatePayload(body): accepts {..} or [ {..}, ... ]; each object must contain string "id".
 *  - merge(aggregateJson, payload): merges one or many entries (keyed by "id") into aggregate snapshot.
 *  - filterByStation(aggregateJson, stationId): returns {"ID": {...}} or {}.
 *  - toJson(value): serializes Java Map/List/atoms back to JSON string.
 * 
 * Aggregate snapshot schema:
 *   { "<id>": { ...entry... }, "<id2>": { ... } }
 */
public final class JsonCodec {
    public static final class JsonError extends RuntimeException {
        public JsonError(String message) { super(message); }
    }

    /** Validate PUT body: must be an object or an array of objects; each must contain string field "id". */
    public static void validatePayload(String body) throws JsonError {
        Object v = parseJson(body);
        if (v instanceof Map<?,?> obj) {
            requireIdString(obj, "payload");
        } else if (v instanceof List<?> arr) {
            if (arr.isEmpty()) throw new JsonError("array payload must not be empty");
            for (int i = 0; i < arr.size(); i++) {
                Object elem = arr.get(i);
                if (!(elem instanceof Map<?,?> m)) {
                    throw new JsonError("array element #" + i + " must be object");
                }
                requireIdString(m, "array element #" + i);
            }
        } else {
            throw new JsonError("payload must be a JSON object or array of objects");
        }
    }

    /**
     * Merge payload (single object or array of objects) into aggregate snapshot keyed by "id".
     * If aggregateJson is missing/invalid, it's treated as {}.
     */
    public static String merge(String aggregateJson, String payload) {
        Map<String,Object> agg = parseAsObjectOrEmpty(aggregateJson);

        Object v = parseJson(payload);
        if (v instanceof Map<?,?> obj) {
            String id = requireIdString(obj, "payload");
            agg.put(id, deepCopy(obj)); // deep copy the entry
        } else if (v instanceof List<?> arr) {
            if (arr.isEmpty()) return toJson(agg);
            for (int i = 0; i < arr.size(); i++) {
                Object elem = arr.get(i);
                if (!(elem instanceof Map<?,?> m)) {
                    throw new JsonError("array element #" + i + " must be object");
                }
                String id = requireIdString(m, "array element #" + i);
                agg.put(id, deepCopy(m));
            }
        } else {
            throw new JsonError("payload must be a JSON object or array of objects");
        }
        return toJson(agg);
    }

    /** Filter by station id: returns {"ID": {...}} or {} if not found. */
    public static String filterByStation(String aggregateJson, String stationId) {
        if (stationId == null || stationId.isBlank()) return normalizeAggregateOrEmpty(aggregateJson);
        Map<String,Object> agg = parseAsObjectOrEmpty(aggregateJson);
        Object entry = agg.get(stationId);
        // If entry is an unexpected type, still serialize via toJson (no strict schema enforcement here)
        Map<String,Object> out = new LinkedHashMap<>();
        if (entry != null) out.put(stationId, entry);
        return toJson(out);
    }

    /** List all top-level keys (station IDs) in the aggregate JSON. */
    public static Set<String> listKeys(String aggregateJson) {
        try {
            Object v = parseJson(aggregateJson);
            if (v instanceof Map<?,?> m) {
                Set<String> keys = new LinkedHashSet<>();
                for (Object k : m.keySet()) {
                    if (k instanceof String s) {
                        keys.add(s);
                    }
                }
                return keys;
            }
        } catch (JsonError e) {
            // ignore parse errors and treat as empty set
        }
        return Collections.emptySet();
    }

    /** Extract the "id" values from a PUT payload JSON (single object or array). */
    public static List<String> extractIds(String payload) throws JsonError {
        Object v = parseJson(payload);
        List<String> ids = new ArrayList<>();
        if (v instanceof Map<?,?> obj) {
            String id = requireIdString(obj, "payload");
            ids.add(id);
        } else if (v instanceof List<?> arr) {
            for (int i = 0; i < arr.size(); i++) {
                Object elem = arr.get(i);
                if (!(elem instanceof Map<?,?> m)) {
                    throw new JsonError("array element #" + i + " must be object");
                }
                String id = requireIdString(m, "array element #" + i);
                ids.add(id);
            }
        } else {
            throw new JsonError("payload must be a JSON object or array of objects");
        }
        return ids;
    }

    /** Remove the given station IDs from the aggregate JSON and return the updated JSON string. */
    public static String removeEntries(String aggregateJson, Collection<String> idsToRemove) {
        Object v = parseJson(aggregateJson);
        if (!(v instanceof Map<?,?> aggMap)) {
            // If aggregate is not an object, nothing to remove
            return normalizeAggregateOrEmpty(aggregateJson);
        }
        // Remove specified keys
        for (String id : idsToRemove) {
            aggMap.remove(id);
        }
        return toJson(aggMap);
    }

    /* ============================ Internal Helpers ============================ */

    /** Ensure object contains string "id"; return the id value. */
    private static String requireIdString(Map<?,?> obj, String where) {
        Object id = obj.get("id");
        if (!(id instanceof String s) || s.isEmpty()) {
            throw new JsonError(where + " must contain string field \"id\"");
        }
        return s;
    }

    /** Parse JSON; if it's an object, copy it into a new Map<String,Object>; else return empty object. */
    private static Map<String,Object> parseAsObjectOrEmpty(String json) {
        try {
            Object v = parseJson(json);
            if (v instanceof Map<?,?> m) {
                return copyToStringObjectMap(m);
            }
            return new LinkedHashMap<>();
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    /** Copy Map<?,?> → Map<String,Object>, validating keys are strings. */
    private static Map<String,Object> copyToStringObjectMap(Map<?,?> src) {
        Map<String,Object> dst = new LinkedHashMap<>();
        for (Map.Entry<?,?> e : src.entrySet()) {
            Object k = e.getKey();
            if (!(k instanceof String ks)) {
                throw new JsonError("object key must be string, got: " + String.valueOf(k));
            }
            dst.put(ks, e.getValue());
        }
        return dst;
    }

    private static String normalizeAggregateOrEmpty(String json) {
        return toJson(parseAsObjectOrEmpty(json));
    }

    /* ============================ JSON Parsing (Lexer + Parser) ============================ */

    private enum Tok { LBRACE, RBRACE, LBRACK, RBRACK, COLON, COMMA, STRING, NUMBER, TRUE, FALSE, NULL, EOF }

    private static final class JsonLexer {
        private final String s;
        private int i = 0;
        private String strVal;
        private String numLexeme;
        private Tok tok = Tok.EOF;

        JsonLexer(String s) { this.s = (s == null) ? "" : s; }

        Tok next() {
            skipWS();
            Tok t;
            if (i >= s.length()) {
                t = Tok.EOF;
            } else {
                char c = s.charAt(i++);
                switch (c) {
                    case '{': t = Tok.LBRACE; break;
                    case '}': t = Tok.RBRACE; break;
                    case '[': t = Tok.LBRACK; break;
                    case ']': t = Tok.RBRACK; break;
                    case ':': t = Tok.COLON;  break;
                    case ',': t = Tok.COMMA;  break;
                    case '"': strVal = scanString(); t = Tok.STRING; break;
                    default:
                        if (c == '-' || isDigit(c)) {
                            i--; // let scanNumber read from this position
                            numLexeme = scanNumber();
                            t = Tok.NUMBER;
                        } else if (c == 't' && matchAhead("rue")) {
                            t = Tok.TRUE;
                        } else if (c == 'f' && matchAhead("alse")) {
                            t = Tok.FALSE;
                        } else if (c == 'n' && matchAhead("ull")) {
                            t = Tok.NULL;
                        } else {
                            throw new JsonError("unexpected char '" + c + "' at pos " + (i - 1));
                        }
        }
    }
    this.tok = t;
    return t;
}

        String strVal()     { return strVal; }
        String numLexeme()  { return numLexeme; }

        private void skipWS() { while (i < s.length() && isWS(s.charAt(i))) i++; }
        private boolean isWS(char c) { return c==' ' || c=='\t' || c=='\r' || c=='\n'; }
        private boolean isDigit(char c) { return c >= '0' && c <= '9'; }

        private boolean matchAhead(String tail) {
            int end = i + tail.length();
            if (end > s.length()) return false;
            boolean ok = s.regionMatches(i, tail, 0, tail.length());
            if (ok) i = end;
            return ok;
        }

        private String scanString() {
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') break;
                if (c == '\\') {
                    if (i >= s.length()) throw new JsonError("unterminated escape");
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (i + 3 >= s.length()) throw new JsonError("bad unicode escape");
                            int code = hex(s.charAt(i++)) << 12 |
                                       hex(s.charAt(i++)) << 8  |
                                       hex(s.charAt(i++)) << 4  |
                                       hex(s.charAt(i++));
                            sb.append((char) code);
                            break;
                        default: throw new JsonError("invalid escape: \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private int hex(char c) {
            if (c >= '0' && c <= '9') return c - '0';
            if (c >= 'a' && c <= 'f') return 10 + (c - 'a');
            if (c >= 'A' && c <= 'F') return 10 + (c - 'A');
            throw new JsonError("invalid hex '" + c + "'");
        }

        private String scanNumber() {
            int start = i;
            if (i < s.length() && s.charAt(i) == '-') i++;
            if (i < s.length() && s.charAt(i) == '0') {
                i++;
            } else {
                while (i < s.length() && isDigit(s.charAt(i))) i++;
            }
            if (i < s.length() && s.charAt(i) == '.') {
                i++;
                if (i >= s.length() || !isDigit(s.charAt(i))) throw new JsonError("bad number fraction");
                while (i < s.length() && isDigit(s.charAt(i))) i++;
            }
            if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                i++;
                if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
                if (i >= s.length() || !isDigit(s.charAt(i))) throw new JsonError("bad number exponent");
                while (i < s.length() && isDigit(s.charAt(i))) i++;
            }
            return s.substring(start, i);
        }
    }

    private static final class JsonParser {
        private final JsonLexer lx;
        private Object val;
        JsonParser(String s) { lx = new JsonLexer(s); }
        Object parse() {
            lx.next(); // get first token
            val = parseValue();
            // Expect EOF at end
            if (lx.tok != Tok.EOF) throw new JsonError("trailing data");
            return val;
        }
        private Object parseValue() {
            switch (lx.tok) {
                case STRING: { String s = lx.strVal(); lx.next(); return s; }
                case NUMBER: { String num = lx.numLexeme(); lx.next(); return parseNumber(num); }
                case TRUE:   { lx.next(); return Boolean.TRUE; }
                case FALSE:  { lx.next(); return Boolean.FALSE; }
                case NULL:   { lx.next(); return null; }
                case LBRACE: return parseObject();
                case LBRACK: return parseArray();
                default: throw new JsonError("unexpected token: " + lx.tok);
            }
        }
        private Map<String,Object> parseObject() {
            Map<String,Object> obj = new LinkedHashMap<>();
            lx.next(); // consume '{'
            if (lx.tok == Tok.RBRACE) {
                lx.next();
                return obj; // empty object
            }
            while (true) {
                if (lx.tok != Tok.STRING) throw new JsonError("expected string key");
                String key = lx.strVal(); lx.next();
                if (lx.tok != Tok.COLON) throw new JsonError("expected ':' after key");
                lx.next();
                Object value = parseValue();
                obj.put(key, value);
                if (lx.tok == Tok.RBRACE) {
                    lx.next();
                    break;
                }
                if (lx.tok != Tok.COMMA) throw new JsonError("expected ',' or '}'");
                lx.next();
            }
            return obj;
        }
        private List<Object> parseArray() {
            List<Object> arr = new ArrayList<>();
            lx.next(); // consume '['
            if (lx.tok == Tok.RBRACK) {
                lx.next();
                return arr; // empty array
            }
            while (true) {
                Object value = parseValue();
                arr.add(value);
                if (lx.tok == Tok.RBRACK) {
                    lx.next();
                    break;
                }
                if (lx.tok != Tok.COMMA) throw new JsonError("expected ',' or ']'");
                lx.next();
            }
            return arr;
        }
        private Number parseNumber(String lexeme) {
            try {
                if (lexeme.contains(".") || lexeme.contains("e") || lexeme.contains("E")) {
                    return Double.parseDouble(lexeme);
                } else {
                    long lv = Long.parseLong(lexeme);
                    if (lv >= Integer.MIN_VALUE && lv <= Integer.MAX_VALUE) {
                        return (int) lv;
                    }
                    return lv;
                }
            } catch (NumberFormatException e) {
                throw new JsonError("invalid number: " + lexeme);
            }
        }
    }

    private static Object parseJson(String s) {
        if (s == null) throw new JsonError("null input");
        String trimmed = s.trim();
        if (trimmed.isEmpty()) throw new JsonError("empty input");
        return new JsonParser(trimmed).parse();
    }

    /** Serialize Java Map/List/atomic value back to JSON string. */
    public static String toJson(Object v) {
        StringBuilder sb = new StringBuilder();
        writeJson(sb, v);
        return sb.toString();
    }

    private static void writeJson(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
            return;
        }
        if (v instanceof String s) {
            writeString(sb, s);
            return;
        }
        if (v instanceof Boolean b) {
            sb.append(b ? "true" : "false");
            return;
        }
        if (v instanceof Number n) {
            sb.append(n.toString());
            return;
        }
        if (v instanceof Map<?,?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?,?> e : m.entrySet()) {
                if (!first) sb.append(',');
                Object k = e.getKey();
                String keyStr;
                if (k instanceof String) {
                    keyStr = (String) k;
                } else {
                    // JSON spec only allows string keys; convert other key types to string
                    keyStr = String.valueOf(k);
                }
                writeString(sb, keyStr);
                sb.append(':');
                writeJson(sb, e.getValue());
                first = false;
            }
            sb.append('}');
            return;
        }
        if (v instanceof List<?> a) {
            sb.append('[');
            boolean first = true;
            for (Object elem : a) {
                if (!first) sb.append(',');
                writeJson(sb, elem);
                first = false;
            }
            sb.append(']');
            return;
        }
        // Fallback for unsupported types: treat as string
        writeString(sb, String.valueOf(v));
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int)c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    private static Object deepCopy(Object v) {
        if (v == null) return null;
        if (v instanceof String || v instanceof Number || v instanceof Boolean) {
            return v;
        }
        if (v instanceof Map<?,?> m) {
            Map<String,Object> dst = new LinkedHashMap<>();
            for (Map.Entry<?,?> e : m.entrySet()) {
                Object k = e.getKey();
                if (!(k instanceof String ks)) {
                    throw new JsonError("object key must be string, got: " + String.valueOf(k));
                }
                dst.put(ks, deepCopy(e.getValue()));
            }
            return dst;
        }
        if (v instanceof List<?> a) {
            List<Object> dst = new ArrayList<>(a.size());
            for (Object e : a) dst.add(deepCopy(e));
            return dst;
        }
        // Fallback: keep as string
        return String.valueOf(v);
    }

    private JsonCodec() {}
}
