package cn.bugstack.domain.trade.service;

import cn.bugstack.domain.trade.model.entity.TradePaySettlementEntity;
import cn.bugstack.domain.trade.model.entity.TradePaySuccessEntity;
import com.google.j2objc.annotations.ObjectiveCName;

import java.util.Map;

public interface ITradeSettlementOrderService {
    /**
     * 营销结算
     * @param tradePaySuccessEntity 交易支付订单实体对象
     * @return 交易结算订单实体
     */
    TradePaySettlementEntity settlementMarketPayOrder(TradePaySuccessEntity tradePaySuccessEntity) throws Exception;

    Map<String, Integer> execSettlementNotifyJob() throws  Exception;

    Map<String, Integer> execSettlementNotifyJob(String teamId) throws Exception;

}
