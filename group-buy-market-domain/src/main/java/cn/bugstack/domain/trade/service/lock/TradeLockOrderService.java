package cn.bugstack.domain.trade.service.lock;

import cn.bugstack.domain.activity.model.entity.*;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.service.IIndexGroupBuyMarketService;
import cn.bugstack.domain.trade.adapter.repository.ITradeRepository;
import cn.bugstack.domain.trade.model.aggregate.GroupBuyOrderAggregate;
import cn.bugstack.domain.trade.model.entity.*;
import cn.bugstack.domain.trade.model.valobj.GroupBuyProgressVO;
import cn.bugstack.domain.trade.service.ITradeLockOrderService;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import javax.annotation.Resource;
import java.util.*;
import static cn.bugstack.domain.trade.service.TradeRequestValidation.required;

@Service
public class TradeLockOrderService implements ITradeLockOrderService {
    @Resource private ITradeRepository repository;
    @Resource private IIndexGroupBuyMarketService indexGroupBuyMarketService;

    @Override
    public MarketPayOrderEntity queryNoPayMarketPayOrderByOutTradeNo(String userId, String outTradeNo) {
        return repository.queryMarketPayOrderEntityByOutTradeNo(userId, outTradeNo);
    }
    @Override
    public GroupBuyProgressVO queryGroupBuyProgress(String teamId) { return repository.queryGroupBuyProgress(teamId); }

    /** The caller supplies identity only. All prices and activity attributes are calculated here. */
    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED, timeout = 30)
    public MarketPayOrderEntity lockMarketPayOrder(UserEntity user, PayActivityEntity requested, PayDiscountEntity input) throws Exception {
        if (user == null || requested == null || input == null || requested.getActivityId() == null || requested.getActivityId() <= 0) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }
        required(user.getUserId(), 64); required(input.getSource(), 8); required(input.getChannel(), 8);
        required(input.getGoodsId(), 16); required(input.getOutTradeNo(), 12); required(input.getNotifyUrl(), 512);
        cn.bugstack.domain.trade.service.TradeRequestValidation.callbackUrl(input.getNotifyUrl());
        String requestedTeam = StringUtils.defaultString(requested.getTeamId());
        if (!requestedTeam.isEmpty()) required(requestedTeam, 8);

        // Locks survive until commit; the second identical request reads the first committed result.
        repository.lockExternalRequest(input.getSource(), input.getChannel(), input.getOutTradeNo());
        MarketPayOrderEntity existing = repository.queryOrderByBusinessKey(input.getSource(), input.getChannel(), input.getOutTradeNo());
        if (existing != null) {
            if (!Objects.equals(existing.getOutTradeNo(), input.getOutTradeNo()) ||
                    !Objects.equals(existing.getUserId(), user.getUserId()) || !Objects.equals(existing.getActivityId(), requested.getActivityId()) ||
                    !Objects.equals(existing.getSource(), input.getSource()) || !Objects.equals(existing.getChannel(), input.getChannel()) ||
                    !Objects.equals(existing.getGoodsId(), input.getGoodsId()) || !Objects.equals(existing.getRequestTeamId(), requestedTeam) ||
                    !Objects.equals(existing.getNotifyUrl(), input.getNotifyUrl())) throw new AppException(ResponseCode.TRADE_CONFLICT);
            return existing;
        }
        repository.lockUserActivity(user.getUserId(), requested.getActivityId());
        // Shared row locks keep the mapping, activity, discount and SKU stable until this order commits.
        repository.lockActivityConfiguration(input.getSource(), input.getChannel(), input.getGoodsId(), requested.getActivityId());
        TrialBalanceEntity trial = indexGroupBuyMarketService.indexMarketTrial(MarketProductEntity.builder()
                .userId(user.getUserId()).source(input.getSource()).channel(input.getChannel())
                .goodsId(input.getGoodsId()).activityId(requested.getActivityId()).build());
        if (trial == null || trial.getGroupBuyActivityDiscountVO() == null) throw new AppException(ResponseCode.E0002);
        GroupBuyActivityDiscountVO activity = trial.getGroupBuyActivityDiscountVO();
        if (!Objects.equals(activity.getActivityId(), requested.getActivityId())) throw new AppException(ResponseCode.ACTIVITY_MISMATCH);
        if (!Boolean.TRUE.equals(trial.getIsEnable())) throw new AppException(ResponseCode.NOT_ELIGIBLE);
        if (!Integer.valueOf(1).equals(activity.getStatus())) throw new AppException(ResponseCode.E0101);
        Date now = new Date();
        if (activity.getStartTime() == null || activity.getEndTime() == null || now.before(activity.getStartTime()) || !now.before(activity.getEndTime())) throw new AppException(ResponseCode.E0102);
        if (activity.getTarget() == null || activity.getTarget() <= 0 || activity.getValidTime() == null || activity.getValidTime() <= 0) throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        int count = repository.queryOrderCountByActivityId(activity.getActivityId(), user.getUserId());
        if (activity.getTakeLimitCount() != null && count >= activity.getTakeLimitCount()) throw new AppException(ResponseCode.E0103);
        cn.bugstack.domain.activity.service.discount.PricePolicy.validateSnapshot(trial.getOriginalPrice(), trial.getDeductionPrice(), trial.getPayPrice());
        PayActivityEntity payActivity = PayActivityEntity.builder().teamId(requestedTeam).activityId(activity.getActivityId())
                .activityName(activity.getActivityName()).startTime(activity.getStartTime()).endTime(activity.getEndTime())
                .targetCount(activity.getTarget()).validTime(activity.getValidTime()).build();
        PayDiscountEntity payDiscount = PayDiscountEntity.builder().source(input.getSource()).channel(input.getChannel())
                .goodsId(input.getGoodsId()).goodsName(trial.getGoodsName()).outTradeNo(input.getOutTradeNo()).notifyUrl(input.getNotifyUrl())
                .originalPrice(trial.getOriginalPrice()).deductionPrice(trial.getDeductionPrice()).payPrice(trial.getPayPrice()).build();
        return repository.lockMarketPayOrder(GroupBuyOrderAggregate.builder().userEntity(user).payActivityEntity(payActivity)
                .payDiscountEntity(payDiscount).userTakeOrderCount(count).build());
    }

}
