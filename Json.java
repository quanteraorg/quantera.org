package com.quantera;

import java.util.*;

/** Tiny JSON reader/writer so the backend needs no third-party libraries. */
public final class Json {
    private Json() {}

    public static Object parse(String s) {
        try {
            P p = new P(s);
            Object v = p.value();
            p.ws();
            if (p.i != s.length()) throw new IllegalArgumentException("trailing data");
            return v;
        } catch (IndexOutOfBoundsException | NumberFormatException e) {
            throw new IllegalArgumentException("invalid json");
        }
    }

    public static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static final class P {
        final String s;
        int i;
        P(String s) { this.s = s; }

        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        void expect(char c) {
            ws();
            if (i >= s.length() || s.charAt(i) != c) throw new IllegalArgumentException("expected " + c);
            i++;
        }

        Object value() {
            ws();
            char c = s.charAt(i);
            if (c == '{') {
                Map<String, Object> m = new LinkedHashMap<>();
                i++; ws();
                if (s.charAt(i) == '}') { i++; return m; }
                while (true) {
                    ws(); String k = str(); expect(':'); m.put(k, value()); ws();
                    if (s.charAt(i) == ',') { i++; continue; }
                    expect('}'); return m;
                }
            }
            if (c == '[') {
                List<Object> l = new ArrayList<>();
                i++; ws();
                if (s.charAt(i) == ']') { i++; return l; }
                while (true) {
                    l.add(value()); ws();
                    if (s.charAt(i) == ',') { i++; continue; }
                    expect(']'); return l;
                }
            }
            if (c == '"') return str();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (st == i) throw new IllegalArgumentException("bad json at " + i);
            return Double.parseDouble(s.substring(st, i));
        }

        String str() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n' -> b.append('\n');
                        case 't' -> b.append('\t');
                        case 'r' -> b.append('\r');
                        case 'b' -> b.append('\b');
                        case 'f' -> b.append('\f');
                        case 'u' -> { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                        default -> b.append(e);
                    }
                } else b.append(c);
            }
        }
    }

    public static String write(Object o) {
        StringBuilder b = new StringBuilder();
        w(o, b);
        return b.toString();
    }

    private static void w(Object o, StringBuilder b) {
        if (o == null) b.append("null");
        else if (o instanceof String s) {
            b.append('"');
            for (char c : s.toCharArray()) {
                switch (c) {
                    case '"' -> b.append("\\\"");
                    case '\\' -> b.append("\\\\");
                    case '\n' -> b.append("\\n");
                    case '\r' -> b.append("\\r");
                    case '\t' -> b.append("\\t");
                    default -> { if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c); }
                }
            }
            b.append('"');
        } else if (o instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) b.append("null");
            else if (d == Math.rint(d) && Math.abs(d) < 1e15) b.append((long) d);
            else b.append(d);
        } else if (o instanceof Boolean x) b.append(x);
        else if (o instanceof Map<?, ?> m) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) b.append(',');
                first = false;
                w(String.valueOf(e.getKey()), b); b.append(':'); w(e.getValue(), b);
            }
            b.append('}');
        } else if (o instanceof Collection<?> c) {
            b.append('[');
            boolean first = true;
            for (Object e : c) { if (!first) b.append(','); first = false; w(e, b); }
            b.append(']');
        } else w(o.toString(), b);
    }
}
