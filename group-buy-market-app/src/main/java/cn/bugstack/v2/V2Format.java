package cn.bugstack.v2;

import cn.bugstack.api.v2.V2Codes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class V2Format {
    private V2Format() {}
    public static String id(String value, int maximum) {
        if (value == null || value.isEmpty() || value.length() > maximum || !value.equals(value.trim()))
            throw new V2Failure(V2Codes.INVALID_PARAMETER);
        return value;
    }
    public static String optionalId(String value, int maximum) {
        if (value == null) return null;
        return id(value, maximum);
    }
    public static long positiveVersion(String value) {
        id(value, 20);
        if (!value.matches("[1-9][0-9]*")) throw new V2Failure(V2Codes.INVALID_PARAMETER);
        try { long result = Long.parseLong(value); if (result > 0) return result; }
        catch (NumberFormatException ignored) { }
        throw new V2Failure(V2Codes.INVALID_PARAMETER);
    }
    public static long activityId(String value) { return positiveVersion(value); }
    public static BigDecimal money(String value) {
        if (value == null || !value.matches("(0|[1-9][0-9]{0,5})\\.[0-9]{2}"))
            throw new V2Failure(V2Codes.INVALID_PARAMETER);
        BigDecimal amount = new BigDecimal(value);
        if (amount.compareTo(new BigDecimal("999999.99")) > 0) throw new V2Failure(V2Codes.INVALID_PARAMETER);
        return amount;
    }
    public static String money(BigDecimal amount) { return amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString(); }
    public static void currency(String currency) {
        if (!"CNY".equals(currency)) throw new V2Failure(V2Codes.UNSUPPORTED_CURRENCY);
    }
    public static Instant instant(String text) {
        if (text == null || !text.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"))
            throw new V2Failure(V2Codes.INVALID_PARAMETER);
        try { return Instant.parse(text); }
        catch (Exception e) { throw new V2Failure(V2Codes.INVALID_PARAMETER); }
    }
    public static String utc(Date date) {
        return date == null ? null : DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
                .withZone(java.time.ZoneOffset.UTC).format(date.toInstant());
    }
    public static String now() { return utc(new Date()); }
    public static String sha256(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b & 0xff));
            return hex.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static String sha256(String text) { return sha256(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }

    public static String canonicalHash(ObjectMapper mapper, JsonNode value) {
        try { return sha256(mapper.writeValueAsString(sorted(mapper, value))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static JsonNode sorted(ObjectMapper mapper, JsonNode value) {
        if (value.isObject()) {
            ObjectNode result=mapper.createObjectNode();
            List<String> names=new ArrayList<>(); value.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            for (String name:names) result.set(name,sorted(mapper,value.get(name)));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result=mapper.createArrayNode();
            for (JsonNode item:value) result.add(sorted(mapper,item));
            return result;
        }
        return value;
    }
}
