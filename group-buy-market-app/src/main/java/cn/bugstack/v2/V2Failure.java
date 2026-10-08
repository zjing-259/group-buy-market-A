package cn.bugstack.v2;

public final class V2Failure extends RuntimeException {
    private final String code;
    public V2Failure(String code) { super(code); this.code = code; }
    public String getCode() { return code; }
}
