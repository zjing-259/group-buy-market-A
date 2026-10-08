package cn.bugstack.api;

import cn.bugstack.api.dto.LockMarketPayOrderRequestDTO;
import cn.bugstack.api.dto.LockMarketPayOrderResponseDTO;
import cn.bugstack.api.dto.SettlementMarketPayOrderRequestDTO;
import cn.bugstack.api.dto.SettlementMarketPayOrderResponseDTO;
import cn.bugstack.api.response.Response;

/**
 * 营销交易服务接口
 * 2026/9/4 16:26
 * @author 正经
 */
public interface IMarketTradeService {
    /**
     * 营销锁单
     * @param lockMarketPayOrderRequestDTO 锁单商品信息
     * @return 锁单结果信息
     */
    Response<LockMarketPayOrderResponseDTO> lockMarketPayOrder(LockMarketPayOrderRequestDTO lockMarketPayOrderRequestDTO);

    /**
     * 营销结算。同一订单及同一支付时间重复请求返回相同结算结果，不重复计数。
     * 已结算订单的支付时间不一致返回 E0113；订单不存在返回 E0104。
     * 历史支付事实核验优先于渠道黑名单；黑名单仅阻止待支付订单首次结算（E0105）。
     * @param requestDTO 结算商品信息
     * @return 结算结果信息
     */
    Response<SettlementMarketPayOrderResponseDTO> settlementMarketPayOrder(SettlementMarketPayOrderRequestDTO requestDTO);

}
