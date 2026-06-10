package cn.bugstack.domain.activity.service.trial.thread;

import cn.bugstack.domain.activity.adapter.repository.IActivityRepository;
import cn.bugstack.domain.activity.model.valobj.SkuVO;
import jdk.nashorn.internal.codegen.CompilerConstants;
import lombok.AllArgsConstructor;

import java.util.concurrent.Callable;

@AllArgsConstructor
public class QuerySkuVOFromDBThreadTask implements Callable<SkuVO> {
    private final String goodsId;
    private final IActivityRepository activityRepository;
    @Override
    public SkuVO call() throws Exception {
        return activityRepository.querySkuByGoodsId(goodsId);
    }
}
