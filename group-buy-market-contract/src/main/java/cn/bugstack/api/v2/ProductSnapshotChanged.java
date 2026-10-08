package cn.bugstack.api.v2;

public class ProductSnapshotChanged implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String goodsId;
    private String productVersion;
    private String goodsName;
    private String originalPrice;
    private String currency;
    private String saleStatus;
    public ProductSnapshotChanged() {}
    public String getGoodsId() { return goodsId; }
    public void setGoodsId(String value) { this.goodsId = value; }
    public String getProductVersion() { return productVersion; }
    public void setProductVersion(String value) { this.productVersion = value; }
    public String getGoodsName() { return goodsName; }
    public void setGoodsName(String value) { this.goodsName = value; }
    public String getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(String value) { this.originalPrice = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { this.currency = value; }
    public String getSaleStatus() { return saleStatus; }
    public void setSaleStatus(String value) { this.saleStatus = value; }
}
