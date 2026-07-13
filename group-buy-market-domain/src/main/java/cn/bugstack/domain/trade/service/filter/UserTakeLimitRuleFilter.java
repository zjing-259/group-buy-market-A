package cn.bugstack.domain.trade.service.filter;

import cn.bugstack.domain.trade.adapter.repository.ITradeRepository;
import cn.bugstack.domain.trade.model.entity.GroupBuyActivityEntity;
import cn.bugstack.domain.trade.model.entity.TradeRuleCommandEntity;
import cn.bugstack.domain.trade.model.entity.TradeRuleFilterBackEntity;
import cn.bugstack.domain.trade.service.factory.TradeRuleFilterFactory;
import cn.bugstack.types.design.framework.link.modle2.handler.ILogicHandler;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class UserTakeLimitRuleFilter implements ILogicHandler<TradeRuleCommandEntity, TradeRuleFilterFactory.DynamicContext, TradeRuleFilterBackEntity> {
    @Resource
    private ITradeRepository repository;

    @Override
    public TradeRuleFilterBackEntity apply(TradeRuleCommandEntity requestParameter, TradeRuleFilterFactory.DynamicContext dynamicContext) throws Exception {

        log.info("用户参与次数校验：{}，activityId：{}", requestParameter.getUserId(), requestParameter.getActivityId());

        Integer count = repository.queryOrderCountByActivityId(requestParameter.getUserId(), requestParameter.getActivityId());

        GroupBuyActivityEntity groupBuyActivityEntity = dynamicContext.getGroupBuyActivityEntity();

        if(null != groupBuyActivityEntity.getTakeLimitCount() && count >= groupBuyActivityEntity.getTakeLimitCount()){
            throw new AppException(ResponseCode.E0103);
        }
        //没超过限制，则返回已参与的次数
        return TradeRuleFilterBackEntity.builder().userTakeOrderCount(count).build();
    }
}
