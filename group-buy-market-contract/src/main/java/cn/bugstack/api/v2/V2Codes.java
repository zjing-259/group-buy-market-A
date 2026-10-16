package cn.bugstack.api.v2;

public final class V2Codes {
    private V2Codes() {}
    public static final String SUCCESS = "0000";
    public static final String INVALID_PARAMETER = "0002";
    public static final String SYSTEM_ERROR = "0001";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String AUTH_REQUIRED = "AUTH_REQUIRED";
    public static final String AUTH_INVALID = "AUTH_INVALID";
    public static final String REPLAY_REJECTED = "REPLAY_REJECTED";
    public static final String PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND";
    public static final String PRODUCT_VERSION_MISMATCH = "PRODUCT_VERSION_MISMATCH";
    public static final String PRODUCT_VERSION_CONFLICT = "PRODUCT_VERSION_CONFLICT";
    public static final String PRODUCT_NOT_SALEABLE = "PRODUCT_NOT_SALEABLE";
    public static final String PRICE_CHANGED = "PRICE_CHANGED";
    public static final String ORDER_CLOSED = "ORDER_CLOSED";
    public static final String ORDER_ALREADY_PAID = "ORDER_ALREADY_PAID";
    public static final String CLOSE_CONFLICT = "CLOSE_CONFLICT";
    public static final String PAYMENT_ID_CONFLICT = "PAYMENT_ID_CONFLICT";
    public static final String PAYMENT_AMOUNT_MISMATCH = "PAYMENT_AMOUNT_MISMATCH";
    public static final String UNSUPPORTED_CURRENCY = "UNSUPPORTED_CURRENCY";
    public static final String LEGACY_FACT_UNVERIFIABLE = "LEGACY_FACT_UNVERIFIABLE";
    public static final String RECONCILIATION_NOT_STARTED = "RECONCILIATION_NOT_STARTED";
    public static final String RECONCILIATION_CONFLICT = "RECONCILIATION_CONFLICT";
    public static final String RECONCILIATION_INCOMPLETE = "RECONCILIATION_INCOMPLETE";
    public static final String REFUND_CONFLICT = "REFUND_CONFLICT";
    public static final String DEPENDENCY_UNAVAILABLE = "DEPENDENCY_UNAVAILABLE";
    public static final String TEAM_DISCOVERY_CONTEXT_CHANGED = "TEAM_DISCOVERY_CONTEXT_CHANGED";
}
