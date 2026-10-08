package cn.bugstack.api.v2;

import java.io.Serializable;

public class RpcResult<T> implements Serializable {
    private static final long serialVersionUID = 1L;
    private String code;
    private String info;
    private T data;
    private String requestId;
    private Long retryAfterMillis;

    public RpcResult() {}
    public RpcResult(String code, String info, T data, String requestId) {
        this.code = code; this.info = info; this.data = data; this.requestId = requestId;
    }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getInfo() { return info; }
    public void setInfo(String info) { this.info = info; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getRetryAfterMillis() { return retryAfterMillis; }
    public void setRetryAfterMillis(Long retryAfterMillis) { this.retryAfterMillis = retryAfterMillis; }
}
