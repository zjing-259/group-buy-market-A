package cn.bugstack.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 商品营销应答对象
 * 不要把页面的所有要的属性，
 * 都用一个个属性字段平铺到类中，那样以后维护会非常复杂。
 * 要思考这些属性的归属问题，定义不同的对象承载。
 * 定义三个对象；Goods - 商品、Team - 拼团、TeamStatistic - 统计。
 * 2026/9/4 16:08
 * @author 正经
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GoodsMarketResponseDTO {
    // 商品信息
    private Goods goods;
    // 组队信息（1个个人的置顶、2个随机的「获取10个，随机取2个」）
    private List<Team> teamList;
    // 组队统计
    private TeamStatistic teamStatistic;

    /**
     * 商品信息
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Goods {
        // 商品ID
        private String goodsId;
        // 活动ID - 前端发起「开团/参团」锁单(lock-market-pay-order)必需
        private Long activityId;
        // 活动名称
        private String activityName;
        // 拼团目标人数 - 「几人团」
        private Integer target;
        // 拼团有效时长 - 开团后需在此时间内成团
        private Integer validTime;
        // 原始价格
        private BigDecimal originalPrice;
        // 折扣金额
        private BigDecimal deductionPrice;
        // 支付价格
        private BigDecimal payPrice;
    }

    /**
     * 组队信息
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Team {
        // 用户ID
        private String userId;
        // 拼单组队ID
        private String teamId;
        // 活动ID
        private Long activityId;
        // 目标数量
        private Integer targetCount;
        // 完成数量
        private Integer completeCount;
        // 锁单数量
        private Integer lockCount;
        // 拼团开始时间 - 参与拼团时间
        private Date validStartTime;
        // 拼团结束时间 - 拼团有效时长
        private Date validEndTime;
        // 倒计时(字符串) validEndTime - validStartTime
        private String validTimeCountdown;
        /** 外部交易单号-确保外部调用唯一幂等 */
        private String outTradeNo;

        //获取倒计时(字符串) validEndTime - validStartTime
        public static String differenceDateTime2Str(Date validStartTime, Date validEndTime) {
            if (validStartTime == null || validEndTime == null) {
                return "无效的时间";
            }

            long diffInMilliseconds = validEndTime.getTime() - validStartTime.getTime();

            if (diffInMilliseconds < 0) {
                return "已结束";
            }

            long seconds = TimeUnit.MILLISECONDS.toSeconds(diffInMilliseconds) % 60;
            long minutes = TimeUnit.MILLISECONDS.toMinutes(diffInMilliseconds) % 60;
            long hours = TimeUnit.MILLISECONDS.toHours(diffInMilliseconds) % 24;
            long days = TimeUnit.MILLISECONDS.toDays(diffInMilliseconds);

            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }

    }

    /**
     * 所有组队统计
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TeamStatistic {
        // 开团队伍数量
        private Integer allTeamCount;
        // 成团队伍数量
        private Integer allTeamCompleteCount;
        // 参团人数总量 - 一个商品的总参团人数
        private Integer allTeamUserCount;
    }
}
