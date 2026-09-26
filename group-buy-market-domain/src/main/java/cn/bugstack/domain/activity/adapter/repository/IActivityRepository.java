package cn.bugstack.domain.activity.adapter.repository;

import cn.bugstack.domain.activity.model.entity.UserGroupBuyOrderDetailEntity;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.model.valobj.SCSkuActivityVO;
import cn.bugstack.domain.activity.model.valobj.SkuVO;
import cn.bugstack.domain.activity.model.valobj.TeamStatisticVO;

import java.util.List;

public interface IActivityRepository {
    GroupBuyActivityDiscountVO queryGroupBuyActivityDiscountVO(Long activityId);

    SkuVO querySkuByGoodsId(String goodsId);

    SCSkuActivityVO querySCSkuActivityBySCGoodsId(String source, String channel, String goodsId);

    boolean isTagCrowRange(String tagId, String userId);

    boolean downgradeSwitch();

    boolean cutRange(String userId);

    //查询**当前用户本人（user_id = userId）**参与的、进行中（未过期）的拼团明细。
    List<UserGroupBuyOrderDetailEntity> queryInProgressUserGroupBuyOrderDetailListByOwner(Long activityId, String userId, Integer ownerCount);
    //查询其他人（user_id != 本人）、尚未满员（团 status=0 拼单中）、进行中的拼团。
    List<UserGroupBuyOrderDetailEntity> queryInProgressUserGroupBuyOrderDetailListByRandom(Long activityId, String userId, Integer randomCount);

    /**
     * 作用：汇总该活动的拼团热度/整体数据。
     * 使用场景：在页面展示"已有 X 人开团、Y 团已成、Z 人参团"，
     * 营造热闹氛围、提供社交证明，促使用户下单参团。
     * 2026/9/5 10:57
     * @author 正经
     */
    TeamStatisticVO queryTeamStatisticByActivityId(Long activityId);

}
