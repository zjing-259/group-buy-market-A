package cn.bugstack.domain.trade.service.settlement;

import cn.bugstack.domain.trade.adapter.port.ITradePort;
import cn.bugstack.domain.trade.adapter.repository.ITradeRepository;
import cn.bugstack.domain.trade.model.aggregate.GroupBuyTeamSettlementAggregate;
import cn.bugstack.domain.trade.model.entity.*;
import cn.bugstack.domain.trade.service.ITradeSettlementOrderService;
import cn.bugstack.domain.trade.service.settlement.factory.TradeSettlementRuleFilterFactory;
import cn.bugstack.types.design.framework.link.model2.chain.BusinessLinkedList;
import cn.bugstack.types.enums.NotifyTaskHTTPEnumVO;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static cn.bugstack.domain.trade.service.TradeRequestValidation.required;

//支付成功后的结算流程
@Slf4j
@Service
public class TradeSettlementOrderService implements ITradeSettlementOrderService {

    @Resource
    private ITradeRepository repository;
    @Resource
    private ITradePort port;
    @Resource
    private BusinessLinkedList<TradeSettlementRuleCommandEntity, TradeSettlementRuleFilterFactory.DynamicContext,TradeSettlementRuleFilterBackEntity> tradeSettlementRuleFilter;

    /**
     * settlementMarketPayOrder作用：支付成功后，把一笔"付款"结算进拼团业务，判定是否成团
     * 2026/9/2 16:50
     * @author 正经
     */
    @Override
    public TradePaySettlementEntity settlementMarketPayOrder(TradePaySuccessEntity tradePaySuccessEntity) throws Exception {
        if (tradePaySuccessEntity == null) throw new cn.bugstack.types.exception.AppException(cn.bugstack.types.enums.ResponseCode.ILLEGAL_PARAMETER);
        required(tradePaySuccessEntity.getUserId(), 64);
        required(tradePaySuccessEntity.getSource(), 8);
        required(tradePaySuccessEntity.getChannel(), 8);
        required(tradePaySuccessEntity.getOutTradeNo(), 12);

        log.info("拼团交易-支付订单结算:{} outTradeNo:{}", tradePaySuccessEntity.getUserId(), tradePaySuccessEntity.getOutTradeNo());

        //1.使用规则链过滤异常并以此查询拼团信息
        TradeSettlementRuleFilterBackEntity tradeSettlementRuleFilterBackEntity = tradeSettlementRuleFilter.apply(
                TradeSettlementRuleCommandEntity.builder()
                        .source(tradePaySuccessEntity.getSource())
                        .channel(tradePaySuccessEntity.getChannel())
                        .userId(tradePaySuccessEntity.getUserId())
                        .outTradeNo(tradePaySuccessEntity.getOutTradeNo())
                        .outTradeTime(tradePaySuccessEntity.getOutTradeTime())
                        .build()
                , new TradeSettlementRuleFilterFactory.DynamicContext()
        );

        String teamId = tradeSettlementRuleFilterBackEntity.getTeamId(); // 团ID，用于后续查询该团信息，并更新该团信息

        //2.组装组团实体
        GroupBuyTeamEntity groupBuyTeamEntity = GroupBuyTeamEntity.builder()
                .teamId(tradeSettlementRuleFilterBackEntity.getTeamId())
                .activityId(tradeSettlementRuleFilterBackEntity.getActivityId())
                .targetCount(tradeSettlementRuleFilterBackEntity.getTargetCount())
                .completeCount(tradeSettlementRuleFilterBackEntity.getCompleteCount())
                .lockCount(tradeSettlementRuleFilterBackEntity.getLockCount())
                .status(tradeSettlementRuleFilterBackEntity.getStatus())
                .validStartTime(tradeSettlementRuleFilterBackEntity.getValidStartTime())
                .validEndTime(tradeSettlementRuleFilterBackEntity.getValidEndTime())
                .notifyUrl(tradeSettlementRuleFilterBackEntity.getNotifyUrl())
                .build();

        // 3. 构建聚合对象，用户 + 团 + 支付成功信息
        GroupBuyTeamSettlementAggregate groupBuyTeamSettlementAggregate = GroupBuyTeamSettlementAggregate.builder()
                .userEntity(UserEntity.builder().userId(tradePaySuccessEntity.getUserId()).build())
                .groupBuyTeamEntity(groupBuyTeamEntity)
                .tradePaySuccessEntity(tradePaySuccessEntity)
                .build();

        // 4. 拼团交易结算（将聚合对象传入）,并把相关数据落库。
        boolean isCompleted = repository.settlementMarketPayOrder(groupBuyTeamSettlementAggregate);
        // The repository has committed the order, team and notification task here.
        // Immediate delivery is best-effort; the durable task remains available to the scheduler.
        if(isCompleted){
            try {
                Map<String, Integer> notifyResultMap = execSettlementNotifyJob(teamId);
                log.info("回调通知拼团完结 result:{}", JSON.toJSONString(notifyResultMap));
            } catch (Exception e) {
                log.error("拼团结算已提交，即时通知失败，保留任务等待补偿 teamId:{}", teamId, e);
            }
        }

        // 5. 返回结算信息 - 公司中开发这样的流程时候，会根据外部需要进行值的设置
        return TradePaySettlementEntity.builder()
                .source(tradePaySuccessEntity.getSource())
                .channel(tradePaySuccessEntity.getChannel())
                .userId(tradePaySuccessEntity.getUserId())
                .teamId(tradeSettlementRuleFilterBackEntity.getTeamId())
                .activityId(groupBuyTeamEntity.getActivityId())
                .outTradeNo(tradePaySuccessEntity.getOutTradeNo())
                .build();
    }

    /**
     * 作用：定时任务入口，扫描全部待发通知并投递。
     * 2026/9/2 16:56
     * @author 正经
     */
    @Override
    public Map<String, Integer> execSettlementNotifyJob() throws Exception {
        log.info("拼团交易-执行结算通知任务");

        // 查询未执行任务
        List<NotifyTaskEntity> notifyTaskEntityList = repository.queryUnExecutedNotifyTaskList();

        return execSettlementNotifyJob(notifyTaskEntityList);
    }

    /**
     * 作用：对指定team回调
     * 2026/9/2 16:57
     * @author 正经
     */
    @Override
    public Map<String, Integer> execSettlementNotifyJob(String teamId) throws Exception {
        log.info("拼团交易-执行结算通知回调，指定 teamId:{}", teamId);
        List<NotifyTaskEntity> notifyTaskEntityList = repository.queryUnExecutedNotifyTaskList(teamId);
        return execSettlementNotifyJob(notifyTaskEntityList);
    }

    private Map<String, Integer> execSettlementNotifyJob(List<NotifyTaskEntity> notifyTaskEntityList) throws Exception {
        int successCount = 0, errorCount = 0, retryCount = 0, skippedCount = 0;
        for (NotifyTaskEntity candidate : notifyTaskEntityList) {
            if (Thread.currentThread().isInterrupted()) break;
            boolean interrupted = false;
            try {
                NotifyTaskEntity task = repository.claimNotifyTask(candidate.getTeamId());
                if (task == null) { skippedCount++; continue; }
                boolean success = false;
                try {
                    String response = port.groupBuyNotify(task);
                    success = NotifyTaskHTTPEnumVO.SUCCESS.getCode().equals(response);
                    if (!success) task.setLastError(response == null ? "EMPTY_RESULT" :
                            "DELIVERY_NOT_ACKNOWLEDGED:" + response.substring(0, Math.min(response.length(), 180)));
                } catch (Exception e) {
                    interrupted = e instanceof InterruptedException;
                    task.setLastError("DELIVERY_EXCEPTION:" + e.getClass().getSimpleName());
                    log.warn("通知投递失败 teamId:{}", task.getTeamId(), e);
                }
                if (success) {
                    successCount += repository.updateNotifyTaskStatusSuccess(task);
                } else if (task.getNotifyCount() >= 5) {
                    errorCount += repository.updateNotifyTaskStatusError(task);
                } else {
                    task.setRetryDelaySeconds(60 * (1 << (task.getNotifyCount() - 1)));
                    retryCount += repository.updateNotifyTaskStatusRetry(task);
                }
            } catch (Exception e) {
                // A committed claim whose result cannot be persisted will recover after its lease expires.
                log.error("通知处理异常，租约到期后恢复 teamId:{}", candidate.getTeamId(), e);
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
        }

        Map<String, Integer> resultMap = new HashMap<>();
        resultMap.put("waitCount", notifyTaskEntityList.size());
        resultMap.put("skippedCount", skippedCount);
        resultMap.put("successCount", successCount);
        resultMap.put("errorCount", errorCount);
        resultMap.put("retryCount", retryCount);

        return resultMap;
    }

}
