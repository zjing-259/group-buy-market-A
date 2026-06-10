package cn.bugstack.domain.activity.service.trial.thread;

import cn.bugstack.domain.activity.adapter.repository.IActivityRepository;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import lombok.AllArgsConstructor;

import java.util.concurrent.Callable;

@AllArgsConstructor
public class QueryGroupBuyActivityDiscountVOThreadTask implements Callable<GroupBuyActivityDiscountVO> {

    private final String source;
    private final String channel;
    private final IActivityRepository repository;

    @Override
    public GroupBuyActivityDiscountVO call() throws Exception {
        return repository.queryGroupBuyActivityDiscountVO(source, channel);
    }
}
