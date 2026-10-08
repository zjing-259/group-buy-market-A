package cn.bugstack.api.v2;

public class BaseRequest implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String source;
    private String channel;
    public BaseRequest() {}
    public String getSource() { return source; }
    public void setSource(String value) { this.source = value; }
    public String getChannel() { return channel; }
    public void setChannel(String value) { this.channel = value; }
}
