package cn.bugstack.domain.trade.service.lock.filter;

import cn.bugstack.domain.trade.adapter.repository.ITradeRepository;
import cn.bugstack.domain.trade.model.entity.GroupBuyActivityEntity;
import cn.bugstack.domain.trade.model.entity.TradeLockRuleCommandEntity;
import cn.bugstack.domain.trade.model.entity.TradeLockRuleFilterBackEntity;
import cn.bugstack.domain.trade.service.lock.factory.TradeLockRuleFilterFactory;
import cn.bugstack.types.design.framework.link.model2.handler.ILogicHandler;
import cn.bugstack.types.enums.ActivityStatusEnumVO;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

@Slf4j
@Service
public class ActivityUsabilityRuleFilter implements ILogicHandler<TradeLockRuleCommandEntity, TradeLockRuleFilterFactory.DynamicContext, TradeLockRuleFilterBackEntity> {
    @Resource
    private ITradeRepository repository;

    @Override
    public TradeLockRuleFilterBackEntity apply(TradeLockRuleCommandEntity requestParameter, TradeLockRuleFilterFactory.DynamicContext dynamicContext) throws Exception {

        log.info("交易规则过滤，活动的可用性校验：{}，activityId：{}", requestParameter.getUserId(), requestParameter.getActivityId());

        GroupBuyActivityEntity groupBuyActivityEntity = repository.queryGroupBuyActivityEntityByActivityId(requestParameter.getActivityId());

        if(!ActivityStatusEnumVO.EFFECTIVE.equals(groupBuyActivityEntity.getStatus())){
            throw new AppException(ResponseCode.E0101);
        }

        //获取当前时间
        Date currentTime = new Date();
        //将当前时间和活动时间对比
        if(currentTime.before(groupBuyActivityEntity.getStartTime()) || currentTime.after(groupBuyActivityEntity.getEndTime())){
            throw new AppException(ResponseCode.E0102);
        }

        //将当前活动信息设置到上下文中
        dynamicContext.setGroupBuyActivityEntity(groupBuyActivityEntity);

        //继续执行下一个过滤器
        return next(requestParameter, dynamicContext);
    }
}
