package com.quantera;

import com.quantera.Support.*;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.time.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

import static com.quantera.Support.*;

/**
 * Price alerts that reach people even when Quantera is closed: browser Web Push (RFC 8030 / 8291 / 8292), implemented with the JDK only.
 * Alerts live in the Supabase table "push_alerts" (service key, server-side). A checker compares them with live prices every minute.
 * Needs VAPID_PUBLIC_KEY and VAPID_PRIVATE_KEY (base64url, P-256); VAPID_SUBJECT is a contact URL or mailto:.
 */
final class Push {
    private Push() {}

    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    static final java.util.regex.Pattern ENDPOINT = java.util.regex.Pattern.compile("^https://[A-Za-z0-9.\\-]+(:\\d+)?/[\\x21-\\x7e]{10,800}$"), B64 = java.util.regex.Pattern.compile("^[A-Za-z0-9_\\-=]{16,200}$");
    static volatile String LAST_ERR;

    static boolean on() { return cfg("VAPID_PUBLIC_KEY", null) != null && cfg("VAPID_PRIVATE_KEY", null) != null && Store.on(); }

    static byte[] b64d(String s) { return Base64.getUrlDecoder().decode(s.replace('+', '-').replace('/', '_').replace("=", "")); }
    static String b64e(byte[] b) { return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }

    static ECParameterSpec P256;
    static {
        try { KeyPairGenerator g = KeyPairGenerator.getInstance("EC"); g.initialize(new ECGenParameterSpec("secp256r1")); P256 = ((ECPublicKey) g.generateKeyPair().getPublic()).getParams(); }
        catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }

    static byte[] raw(ECPublicKey k) {
        byte[] x = fixed(k.getW().getAffineX()), y = fixed(k.getW().getAffineY()), out = new byte[65];
        out[0] = 4; System.arraycopy(x, 0, out, 1, 32); System.arraycopy(y, 0, out, 33, 32);
        return out;
    }
    static byte[] fixed(BigInteger v) { byte[] b = v.toByteArray(), o = new byte[32]; int n = Math.min(32, b.length); System.arraycopy(b, b.length - n, o, 32 - n, n); return o; }
    static ECPublicKey pub(byte[] raw) throws GeneralSecurityException {
        if (raw.length != 65 || raw[0] != 4) throw new GeneralSecurityException("bad key");
        ECPoint p = new ECPoint(new BigInteger(1, Arrays.copyOfRange(raw, 1, 33)), new BigInteger(1, Arrays.copyOfRange(raw, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(p, P256));
    }
    static ECPrivateKey priv(byte[] d) throws GeneralSecurityException { return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, d), P256)); }

    static byte[] hmac(byte[] key, byte[]... parts) throws GeneralSecurityException { Mac m = Mac.getInstance("HmacSHA256"); m.init(new SecretKeySpec(key, "HmacSHA256")); for (byte[] p : parts) m.update(p); return m.doFinal(); }
    static byte[] expand(byte[] prk, byte[] info, int len) throws GeneralSecurityException { return Arrays.copyOf(hmac(prk, info, new byte[]{1}), len); }
    static byte[] cat(byte[]... a) { int n = 0; for (byte[] x : a) n += x.length; ByteBuffer b = ByteBuffer.allocate(n); for (byte[] x : a) b.put(x); return b.array(); }

    /** RFC 8291 aes128gcm body for one subscription. */
    static byte[] encrypt(byte[] uaPublic, byte[] authSecret, byte[] payload) throws GeneralSecurityException {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC"); g.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair as = g.generateKeyPair();
        KeyAgreement ka = KeyAgreement.getInstance("ECDH"); ka.init(as.getPrivate()); ka.doPhase(pub(uaPublic), true);
        byte[] ecdh = ka.generateSecret(), asPublic = raw((ECPublicKey) as.getPublic());
        byte[] prkKey = hmac(authSecret, ecdh), ikm = expand(prkKey, cat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic), 32);
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        byte[] prk = hmac(salt, ikm), cek = expand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16), nonce = expand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ct = c.doFinal(cat(payload, new byte[]{2}));
        return cat(salt, ByteBuffer.allocate(4).putInt(4096).array(), new byte[]{65}, asPublic, ct);
    }

    /** RFC 8292 VAPID header for the push service at this endpoint. */
    static String vapid(String endpoint) throws GeneralSecurityException {
        URI u = URI.create(endpoint);
        String aud = u.getScheme() + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
        String h = b64e("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String c = b64e(Json.write(Json.obj("aud", aud, "exp", Instant.now().getEpochSecond() + 12 * 3600, "sub", cfg("VAPID_SUBJECT", "https://quanteraorg.vercel.app"))).getBytes(StandardCharsets.UTF_8));
        Signature s = Signature.getInstance("SHA256withECDSAinP1363Format"); s.initSign(priv(b64d(cfg("VAPID_PRIVATE_KEY", ""))));
        s.update((h + "." + c).getBytes(StandardCharsets.US_ASCII));
        return "vapid t=" + h + "." + c + "." + b64e(s.sign()) + ", k=" + cfg("VAPID_PUBLIC_KEY", "");
    }

    /** Sends one notification. Returns the push service's status (201 = delivered to the service, 404/410 = subscription gone). */
    static int send(String endpoint, String p256dh, String auth, Map<String, Object> msg) {
        try {
            byte[] body = encrypt(b64d(p256dh), b64d(auth), Json.write(msg).getBytes(StandardCharsets.UTF_8));
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(10)).header("Content-Encoding", "aes128gcm").header("Content-Type", "application/octet-stream")
                    .header("TTL", "86400").header("Urgency", "high").header("Authorization", vapid(endpoint)).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 != 2) LAST_ERR = Instant.now().toString().substring(0, 19) + " push: HTTP " + r.statusCode();
            return r.statusCode();
        } catch (Exception e) { LAST_ERR = Instant.now().toString().substring(0, 19) + " push: " + e.getClass().getSimpleName(); return 0; }
    }

    // ---------------------------------------------------------------- API

    record Sub(String endpoint, String p256dh, String auth) {}
    static Sub sub(Object body) {
        Map<String, Object> s = map(map(body).get("subscription")), k = map(s.get("keys"));
        String e = str(s.get("endpoint")), p = str(k.get("p256dh")), a = str(k.get("auth"));
        boolean local = cfg("PUSH_ALLOW_LOCAL", null) != null && e != null && e.startsWith("http://127.0.0.1:"); // tests only
        if (e == null || p == null || a == null || (!local && !ENDPOINT.matcher(e).matches()) || !B64.matcher(p).matches() || !B64.matcher(a).matches()) throw new UpstreamException("Invalid push subscription", 400);
        return new Sub(e, p, a);
    }
    static String enc(String s) { return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8); }
    static void need() { if (!on()) throw new UpstreamException("Background alerts are not set up yet", 503); }

    static Map<String, Object> key() { need(); return Json.obj("publicKey", cfg("VAPID_PUBLIC_KEY", "")); }

    static Map<String, Object> add(Object body) {
        need();
        Sub s = sub(body);
        Map<String, Object> b = map(body);
        String sym = String.valueOf(b.getOrDefault("symbol", "")).trim().toUpperCase(), op = String.valueOf(b.getOrDefault("op", ""));
        Double px = num(b.get("price"));
        if (!SYM.matcher(sym).matches() || !(op.equals(">") || op.equals("<")) || px == null || px <= 0 || px > 1e9) throw new UpstreamException("Invalid alert", 400);
        List<Object> mine = list(Store.rest("GET", "push_alerts?select=id&fired_at=is.null&endpoint=eq." + enc(s.endpoint()), null));
        if (mine.size() >= 50) throw new UpstreamException("You can have up to 50 active alerts", 400);
        List<Object> r = list(Store.rest("POST", "push_alerts", List.of(Json.obj("endpoint", s.endpoint(), "p256dh", s.p256dh(), "auth", s.auth(), "symbol", sym, "op", op, "price", px))));
        return Json.obj("ok", true, "id", r.isEmpty() ? null : map(r.get(0)).get("id"));
    }

    static Map<String, Object> listFor(Object body) {
        need();
        Sub s = sub(body);
        return Json.obj("alerts", Store.rest("GET", "push_alerts?select=id,symbol,op,price,created_at,fired_at,fired_price&order=created_at.desc&limit=100&endpoint=eq." + enc(s.endpoint()), null));
    }

    static Map<String, Object> remove(Object body) {
        need();
        Sub s = sub(body);
        Double id = num(map(body).get("id"));
        if (id == null) throw new UpstreamException("Invalid alert", 400);
        Store.rest("DELETE", "push_alerts?id=eq." + id.longValue() + "&endpoint=eq." + enc(s.endpoint()), null); // only the owner (the holder of the endpoint) can delete
        return Json.obj("ok", true);
    }

    static Map<String, Object> test(Object body) {
        need();
        Sub s = sub(body);
        int code = send(s.endpoint(), s.p256dh(), s.auth(), Json.obj("title", "Quantera alerts are on", "body", "You’ll get a notification like this when a price alert triggers, even with Quantera closed.", "url", "/#/trade"));
        if (code / 100 != 2) throw new UpstreamException("Could not send a test notification", 502);
        return Json.obj("ok", true);
    }

    /** Every minute: price-check active alerts, notify the ones that triggered, mark them fired, and drop dead subscriptions. */
    static void check() {
        if (!on()) return;
        List<Object> rows;
        try { rows = list(Store.rest("GET", "push_alerts?select=id,endpoint,p256dh,auth,symbol,op,price&fired_at=is.null&limit=2000", null)); }
        catch (RuntimeException e) { LAST_ERR = "alerts: storage " + e.getMessage(); return; }
        Map<String, Map<String, Object>> px = new HashMap<>();
        for (Object o : rows) {
            Map<String, Object> a = map(o);
            String sym = str(a.get("symbol"));
            Map<String, Object> q = px.computeIfAbsent(sym, k -> { try { return Providers.quote(k, 60_000); } catch (RuntimeException e) { return Map.of(); } });
            Double p = num(q.get("price")), lim = num(a.get("price"));
            if (p == null || lim == null) continue;
            boolean hit = ">".equals(a.get("op")) ? p >= lim : p <= lim;
            if (!hit) continue;
            String cur = str(q.get("currency")), money = (cur == null || cur.equals("USD") ? "$" : "") + String.format(Locale.US, "%,.2f", p) + (cur == null || cur.equals("USD") ? "" : " " + cur);
            int code = send(str(a.get("endpoint")), str(a.get("p256dh")), str(a.get("auth")), Json.obj("title", sym + " is " + (">".equals(a.get("op")) ? "above " : "below ") + String.format(Locale.US, "%,.2f", lim),
                    "body", "Now " + money + ". Tap to open the chart. Your Quantera price alert.", "url", "/#/trade/" + enc(sym), "tag", "alert-" + a.get("id")));
            try {
                if (code == 404 || code == 410) Store.rest("DELETE", "push_alerts?endpoint=eq." + enc(str(a.get("endpoint"))), null);
                else if (code / 100 == 2) Store.rest("PATCH", "push_alerts?id=eq." + num(a.get("id")).longValue(), Json.obj("fired_at", Instant.now().toString(), "fired_price", p));
            } catch (RuntimeException ignored) {}
        }
    }

    static void start() {
        if (!on()) return;
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory()).scheduleWithFixedDelay(() -> { try { check(); } catch (Throwable t) { LAST_ERR = "alerts: " + t.getClass().getSimpleName(); } }, 20, 60, java.util.concurrent.TimeUnit.SECONDS);
        System.out.println("Background price alerts on");
    }
}
