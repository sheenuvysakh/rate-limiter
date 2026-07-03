package org.router.ratelimiter.util;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class RequestFingerPrinter {

    public static String getFingerprint(HttpServletRequest request) {
        // Check if an enterprise edge proxy (like Cloudflare JA4) already did the heavy lifting
        String edgeFingerprint = request.getHeader("CF-JA4");
        if (edgeFingerprint != null && !edgeFingerprint.isBlank()) {
            return edgeFingerprint;
        }

        // Fallback:- Generate a combined fingerprint from IP & User-Agent
        String ip = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) userAgent = "unknown-agent";

        // Hash them together so raw IPs aren't exposed in Redis plain text
        return sha256(ip + "|" + userAgent);
    }

    private static String getClientIp(HttpServletRequest request) {
        // Look through common reverse-proxy headers first
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // Then handling multi-hop proxies (take the first client IP)
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(input.hashCode()); // Crude fallback
        }
    }
}