package cn.bugstack.domain.activity.service.trial.thread;

import cn.bugstack.domain.activity.adapter.repository.IActivityRepository;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.model.valobj.SCSkuActivityVO;
import lombok.AllArgsConstructor;

import java.util.concurrent.Callable;

@AllArgsConstructor
public class QueryGroupBuyActivityDiscountVOThreadTask implements Callable<GroupBuyActivityDiscountVO> {

    private final String source;
    private final String channel;
    private final String goodsId;
    private final IActivityRepository repository;

    @Override
    public GroupBuyActivityDiscountVO call() throws Exception {
        SCSkuActivityVO skuActivityVO = repository.querySCSkuActivityBySCGoodsId(source, channel, goodsId);
        if(null == skuActivityVO)  return null;

        return repository.queryGroupBuyActivityDiscountVO(skuActivityVO.getActivityId());
    }
}
