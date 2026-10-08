package cn.bugstack.domain.trade.adapter.port;

import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;

public interface ITradePort {

    /** 在持有任务认领后调用；仅精确 success 表示送达，其余结果及异常均为失败。 */
    String groupBuyNotify(NotifyTaskEntity notifyTask) throws Exception;

}
