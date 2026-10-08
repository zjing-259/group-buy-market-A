package cn.bugstack.infrastructure.dao;

import org.apache.ibatis.annotations.*;

/** Transaction-owned database locks: external request, user quota, then shared configuration. */
@Mapper
public interface ITradeGuardDao {
    @Select("SELECT CURRENT_TIMESTAMP(3)")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    java.util.Date currentTime();
    @Insert("INSERT INTO trade_request_guard(source,channel,out_trade_no) VALUES(#{source},#{channel},#{outTradeNo}) ON DUPLICATE KEY UPDATE out_trade_no=out_trade_no")
    void lockRequest(@Param("source") String source, @Param("channel") String channel, @Param("outTradeNo") String outTradeNo);
    @Insert("INSERT INTO trade_quota_guard(user_id,activity_id) VALUES(#{userId},#{activityId}) ON DUPLICATE KEY UPDATE activity_id=activity_id")
    void lockQuota(@Param("userId") String userId, @Param("activityId") Long activityId);
    @Select("SELECT activity_id FROM sc_sku_activity WHERE source=#{source} AND channel=#{channel} AND goods_id=#{goodsId} LOCK IN SHARE MODE")
    Long lockMapping(@Param("source") String source, @Param("channel") String channel, @Param("goodsId") String goodsId);
    @Select("SELECT discount_id FROM group_buy_activity WHERE activity_id=#{activityId} LOCK IN SHARE MODE")
    String lockActivity(Long activityId);
    @Select("SELECT discount_id FROM group_buy_discount WHERE discount_id=#{discountId} LOCK IN SHARE MODE")
    String lockDiscount(String discountId);
    @Select("SELECT goods_id FROM sku WHERE goods_id=#{goodsId} LOCK IN SHARE MODE")
    String lockSku(String goodsId);
}
