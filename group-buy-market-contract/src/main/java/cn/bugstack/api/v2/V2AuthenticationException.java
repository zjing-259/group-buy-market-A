package cn.bugstack.api.v2;

/** Transport-level rejection raised before a v2 business method is invoked. */
public final class V2AuthenticationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String code;

    public V2AuthenticationException(String code) { super(code); this.code=code; }
    public String getCode() { return code; }
}
