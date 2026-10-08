package cn.bugstack.api.v2;

public class TrialResponse implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private Boolean available;
    private Boolean visible;
    private Boolean eligible;
    private String reasonCode;
    private Offer offer;
    private String quotedAt;
    public TrialResponse() {}
    public Boolean getAvailable() { return available; }
    public void setAvailable(Boolean value) { this.available = value; }
    public Boolean getVisible() { return visible; }
    public void setVisible(Boolean value) { this.visible = value; }
    public Boolean getEligible() { return eligible; }
    public void setEligible(Boolean value) { this.eligible = value; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String value) { this.reasonCode = value; }
    public Offer getOffer() { return offer; }
    public void setOffer(Offer value) { this.offer = value; }
    public String getQuotedAt() { return quotedAt; }
    public void setQuotedAt(String value) { this.quotedAt = value; }
}
