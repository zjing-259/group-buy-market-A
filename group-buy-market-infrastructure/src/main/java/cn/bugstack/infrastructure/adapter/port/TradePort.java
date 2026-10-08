package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.trade.adapter.port.ITradePort;
import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;
import cn.bugstack.domain.trade.service.TradeRequestValidation;
import cn.bugstack.infrastructure.gateway.GroupBuyNotifyService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;

/** The database claim covers both delivery and result persistence. */
@Service
public class TradePort implements ITradePort {
    @Resource private GroupBuyNotifyService groupBuyNotifyService;

    @Override
    public String groupBuyNotify(NotifyTaskEntity task) throws Exception {
        TradeRequestValidation.callbackUrl(task.getNotifyUrl());
        return groupBuyNotifyService.groupBuyNotify(task.getNotifyUrl(), task.getParameterJson());
    }
}
