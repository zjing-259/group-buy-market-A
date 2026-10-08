package cn.bugstack.api.v2;

public class Offer implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String activityId;
    private String goodsId;
    private String originalPrice;
    private String deductionPrice;
    private String payPrice;
    private String currency;
    private Integer targetCount;
    private String productVersion;
    public Offer() {}
    public String getActivityId() { return activityId; }
    public void setActivityId(String value) { this.activityId = value; }
    public String getGoodsId() { return goodsId; }
    public void setGoodsId(String value) { this.goodsId = value; }
    public String getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(String value) { this.originalPrice = value; }
    public String getDeductionPrice() { return deductionPrice; }
    public void setDeductionPrice(String value) { this.deductionPrice = value; }
    public String getPayPrice() { return payPrice; }
    public void setPayPrice(String value) { this.payPrice = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { this.currency = value; }
    public Integer getTargetCount() { return targetCount; }
    public void setTargetCount(Integer value) { this.targetCount = value; }
    public String getProductVersion() { return productVersion; }
    public void setProductVersion(String value) { this.productVersion = value; }
}
