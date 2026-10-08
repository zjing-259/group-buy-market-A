package cn.bugstack.domain.trade.service.settlement.filter;

import cn.bugstack.domain.trade.adapter.repository.ITradeRepository;
import cn.bugstack.domain.trade.model.entity.MarketPayOrderEntity;
import cn.bugstack.domain.trade.model.entity.TradeSettlementRuleCommandEntity;
import cn.bugstack.domain.trade.model.entity.TradeSettlementRuleFilterBackEntity;
import cn.bugstack.domain.trade.service.settlement.factory.TradeSettlementRuleFilterFactory;
import cn.bugstack.types.design.framework.link.model2.handler.ILogicHandler;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class OutTradeNoRuleFilter implements ILogicHandler<TradeSettlementRuleCommandEntity, TradeSettlementRuleFilterFactory.DynamicContext, TradeSettlementRuleFilterBackEntity> {
    @Resource
    private ITradeRepository repository;
    @Override
    public TradeSettlementRuleFilterBackEntity apply(TradeSettlementRuleCommandEntity requestParameter, TradeSettlementRuleFilterFactory.DynamicContext dynamicContext) throws Exception {
        log.info("结算规则过滤-外部单号校验{} outTradeNo{}", requestParameter.getUserId(), requestParameter.getOutTradeNo());

        //1.查询拼团信息
        MarketPayOrderEntity marketPayOrderEntity = repository.queryOrderByBusinessKey(requestParameter.getSource(), requestParameter.getChannel(), requestParameter.getOutTradeNo());
        if(null == marketPayOrderEntity){
            log.info("不存在的外部交易单号或用户已退单，不需要做支付订单结算:{} outTradeNo:{}", requestParameter.getUserId(), requestParameter.getOutTradeNo());
            throw new AppException(ResponseCode.E0104);
        }

        if (!java.util.Objects.equals(marketPayOrderEntity.getOutTradeNo(), requestParameter.getOutTradeNo())) {
            throw new AppException(ResponseCode.TRADE_CONFLICT);
        }
        if (!java.util.Objects.equals(marketPayOrderEntity.getUserId(), requestParameter.getUserId()) ||
                !java.util.Objects.equals(marketPayOrderEntity.getSource(), requestParameter.getSource()) ||
                !java.util.Objects.equals(marketPayOrderEntity.getChannel(), requestParameter.getChannel())) {
            throw new AppException(ResponseCode.CHANNEL_MISMATCH);
        }
        if (marketPayOrderEntity.getTradeOrderStatusEnumVO() == cn.bugstack.domain.trade.model.valobj.TradeOrderStatusEnumVO.COMPLETE &&
                (marketPayOrderEntity.getOutTradeTime() == null || requestParameter.getOutTradeTime() == null ||
                        marketPayOrderEntity.getOutTradeTime().getTime() != requestParameter.getOutTradeTime().getTime())) {
            throw new AppException(ResponseCode.PAYMENT_CONFLICT);
        }
        if (marketPayOrderEntity.getTradeOrderStatusEnumVO() != cn.bugstack.domain.trade.model.valobj.TradeOrderStatusEnumVO.CREATE &&
                marketPayOrderEntity.getTradeOrderStatusEnumVO() != cn.bugstack.domain.trade.model.valobj.TradeOrderStatusEnumVO.COMPLETE) {
            throw new AppException(ResponseCode.E0104);
        }
        //写入上下文
        dynamicContext.setMarketPayOrderEntity(marketPayOrderEntity);

        return next(requestParameter, dynamicContext);
    }
}
