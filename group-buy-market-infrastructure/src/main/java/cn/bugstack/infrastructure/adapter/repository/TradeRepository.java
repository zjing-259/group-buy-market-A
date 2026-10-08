package cn.bugstack.infrastructure.adapter.repository;

import cn.bugstack.domain.trade.adapter.repository.ITradeRepository;
import cn.bugstack.domain.trade.model.aggregate.GroupBuyOrderAggregate;
import cn.bugstack.domain.trade.model.aggregate.GroupBuyTeamSettlementAggregate;
import cn.bugstack.domain.trade.model.entity.*;
import cn.bugstack.domain.trade.model.valobj.GroupBuyProgressVO;
import cn.bugstack.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import cn.bugstack.infrastructure.dao.IGroupBuyActivityDao;
import cn.bugstack.infrastructure.dao.IGroupBuyOrderDao;
import cn.bugstack.infrastructure.dao.IGroupBuyOrderListDao;
import cn.bugstack.infrastructure.dao.INotifyTaskDao;
import cn.bugstack.infrastructure.dao.po.GroupBuyActivity;
import cn.bugstack.infrastructure.dao.po.GroupBuyOrder;
import cn.bugstack.infrastructure.dao.po.GroupBuyOrderList;
import cn.bugstack.infrastructure.dao.po.NotifyTask;
import cn.bugstack.infrastructure.dcc.DCCService;
import cn.bugstack.types.enums.ActivityStatusEnumVO;
import cn.bugstack.types.enums.GroupBuyOrderEnumVO;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;

import javax.annotation.Resource;
import java.util.*;

@Repository
public class TradeRepository implements ITradeRepository {
    @Resource
    private IGroupBuyOrderDao groupBuyOrderDao;
    @Resource
    private IGroupBuyOrderListDao groupBuyOrderListDao;
    @Resource
    private IGroupBuyActivityDao groupBuyActivityDao;
    @Resource
    private INotifyTaskDao notifyTaskDao;
    @Resource
    private DCCService dccService;
    @Resource
    private cn.bugstack.infrastructure.dao.ITradeGuardDao tradeGuardDao;

    @Override
    public void lockExternalRequest(String source, String channel, String outTradeNo) {
        tradeGuardDao.lockRequest(source, channel, outTradeNo);
    }

    @Override
    public void lockUserActivity(String userId, Long activityId) {
        tradeGuardDao.lockQuota(userId, activityId);
    }

    @Override
    public void lockActivityConfiguration(String source, String channel, String goodsId, Long activityId) {
        Long mapped = tradeGuardDao.lockMapping(source, channel, goodsId);
        if (!Objects.equals(mapped, activityId)) throw new AppException(ResponseCode.ACTIVITY_MISMATCH);
        String discountId = tradeGuardDao.lockActivity(activityId);
        if (discountId == null || tradeGuardDao.lockDiscount(discountId) == null || tradeGuardDao.lockSku(goodsId) == null) {
            throw new AppException(ResponseCode.E0002);
        }
    }

    @Override
    public MarketPayOrderEntity queryOrderByBusinessKey(String source, String channel, String outTradeNo) {
        return toOrderEntity(groupBuyOrderListDao.queryOrderByBusinessKey(GroupBuyOrderList.builder()
                .source(source).channel(channel).outTradeNo(outTradeNo).build()));
    }

    private MarketPayOrderEntity toOrderEntity(GroupBuyOrderList row) {
        if (row == null) return null;
        return MarketPayOrderEntity.builder().orderId(row.getOrderId()).teamId(row.getTeamId())
                .outTradeNo(row.getOutTradeNo())
                .userId(row.getUserId()).activityId(row.getActivityId()).goodsId(row.getGoodsId())
                .source(row.getSource()).channel(row.getChannel()).requestTeamId(row.getRequestTeamId())
                .notifyUrl(row.getNotifyUrl()).originalPrice(row.getOriginalPrice()).payPrice(row.getPayPrice())
                .deductionPrice(row.getDeductionPrice()).createTime(row.getCreateTime()).outTradeTime(row.getOutTradeTime())
                .tradeOrderStatusEnumVO(TradeOrderStatusEnumVO.valueOf(row.getStatus())).build();
    }
    @Override
    public GroupBuyProgressVO queryGroupBuyProgress(String teamId) {
        GroupBuyOrder groupBuyOrder = groupBuyOrderDao.queryGroupBuyProgress(teamId);

        if(null == groupBuyOrder) return null;

        return GroupBuyProgressVO.builder()
                .targetCount(groupBuyOrder.getTargetCount())
                .completeCount(groupBuyOrder.getCompleteCount())
                .lockCount(groupBuyOrder.getLockCount())
                .build();
    }

    @Transactional(timeout = 500)
    @Override
    public MarketPayOrderEntity lockMarketPayOrder(GroupBuyOrderAggregate groupBuyOrderAggregate) {
        //聚合对象信息
        UserEntity userEntity = groupBuyOrderAggregate.getUserEntity();
        PayActivityEntity payActivityEntity = groupBuyOrderAggregate.getPayActivityEntity();
        PayDiscountEntity payDiscountEntity = groupBuyOrderAggregate.getPayDiscountEntity();
        cn.bugstack.domain.activity.service.discount.PricePolicy.validateSnapshot(
                payDiscountEntity.getOriginalPrice(), payDiscountEntity.getDeductionPrice(), payDiscountEntity.getPayPrice());
        //用户参与次数（后面需加1）
        Integer userTakeOrderCount = groupBuyOrderAggregate.getUserTakeOrderCount();

        String teamId = payActivityEntity.getTeamId();

        // NOW() is fixed at statement start. Acquire the team lock before issuing time-sensitive SQL.
        if (StringUtils.isNotBlank(teamId) && groupBuyOrderDao.lockTeamById(teamId) == null) {
            throw new AppException(ResponseCode.TEAM_INVALID);
        }
        Date reservationTime = tradeGuardDao.currentTime();
        if (payActivityEntity.getStartTime() == null || payActivityEntity.getEndTime() == null ||
                reservationTime.before(payActivityEntity.getStartTime()) || !reservationTime.before(payActivityEntity.getEndTime())) {
            throw new AppException(ResponseCode.E0102);
        }

        //判断用户是发起拼团还是加入已有的团
        if(StringUtils.isBlank(teamId)){
            teamId = RandomStringUtils.randomNumeric(8);

            //构建拼团订单
            GroupBuyOrder groupBuyOrder = GroupBuyOrder.builder()
                    .teamId(teamId)
                    .activityId(payActivityEntity.getActivityId())
                    .goodsId(payDiscountEntity.getGoodsId())
                    .source(payDiscountEntity.getSource())
                    .channel(payDiscountEntity.getChannel())
                    .originalPrice(payDiscountEntity.getOriginalPrice())
                    .deductionPrice(payDiscountEntity.getDeductionPrice())
                    .payPrice(payDiscountEntity.getPayPrice())
                    .targetCount(payActivityEntity.getTargetCount())
                    .completeCount(0)
                    .lockCount(1)
                    .validTime(payActivityEntity.getValidTime())
                    .activityStartTime(payActivityEntity.getStartTime())
                    .activityEndTime(payActivityEntity.getEndTime())
                    .notifyUrl(payDiscountEntity.getNotifyUrl())
                    .build();
            //将订单插入数据库
            if (groupBuyOrderDao.insert(groupBuyOrder) != 1) throw new AppException(ResponseCode.E0102);
        }else{
            int updateAddLockCount = groupBuyOrderDao.updateAddLockCount(GroupBuyOrder.builder()
                    .teamId(teamId).activityId(payActivityEntity.getActivityId()).goodsId(payDiscountEntity.getGoodsId())
                    .source(payDiscountEntity.getSource()).channel(payDiscountEntity.getChannel())
                    .activityStartTime(payActivityEntity.getStartTime()).activityEndTime(payActivityEntity.getEndTime())
                    .notifyUrl(payDiscountEntity.getNotifyUrl()).build());
            //如果返回的影响行不是1就报异常
            if(1 != updateAddLockCount){
                throw new AppException(ResponseCode.TEAM_INVALID);
            }
        }

        String orderId = RandomStringUtils.randomNumeric(12);
        GroupBuyOrderList groupBuyOrderListReq = GroupBuyOrderList.builder()
                .userId(userEntity.getUserId())
                .teamId(teamId)
                .orderId(orderId)
                .activityId(payActivityEntity.getActivityId())
                .startTime(payActivityEntity.getStartTime())
                .endTime(payActivityEntity.getEndTime())
                .goodsId(payDiscountEntity.getGoodsId())
                .source(payDiscountEntity.getSource())
                .channel(payDiscountEntity.getChannel())
                .originalPrice(payDiscountEntity.getOriginalPrice())
                .deductionPrice(payDiscountEntity.getDeductionPrice())
                .status(TradeOrderStatusEnumVO.CREATE.getCode())
                .outTradeNo(payDiscountEntity.getOutTradeNo())
                .payPrice(payDiscountEntity.getPayPrice())
                .requestTeamId(StringUtils.defaultString(payActivityEntity.getTeamId()))
                .notifyUrl(payDiscountEntity.getNotifyUrl())
                .bizId(org.apache.commons.codec.digest.DigestUtils.sha256Hex(payActivityEntity.getActivityId() + ":" + userEntity.getUserId() + ":" + (userTakeOrderCount + 1)))
                .build();
        //检查唯一索引冲突
        try{
            if (groupBuyOrderListDao.insert(groupBuyOrderListReq) != 1) throw new AppException(ResponseCode.TEAM_INVALID);
        }catch (DuplicateKeyException e){
            throw new AppException(ResponseCode.INDEX_EXCEPTION);
        }
        //构建返回对象
        return MarketPayOrderEntity.builder()
                .outTradeNo(payDiscountEntity.getOutTradeNo())
                .teamId(teamId)
                .originalPrice(payDiscountEntity.getOriginalPrice())
                .payPrice(payDiscountEntity.getPayPrice())
                .orderId(orderId)
                .deductionPrice(payDiscountEntity.getDeductionPrice())
                .tradeOrderStatusEnumVO(TradeOrderStatusEnumVO.CREATE)
                .build();
    }

    @Override
    public GroupBuyActivityEntity queryGroupBuyActivityEntityByActivityId(Long activityId) {

        GroupBuyActivity groupBuyActivity = groupBuyActivityDao.queryGroupBuyActivityByActivityId(activityId);

        if (groupBuyActivity == null) throw new AppException(ResponseCode.E0101);
        return GroupBuyActivityEntity.builder()
                .activityId(groupBuyActivity.getActivityId())
                .activityName(groupBuyActivity.getActivityName())
                .discountId(groupBuyActivity.getDiscountId())
                .groupType(groupBuyActivity.getGroupType())
                .takeLimitCount(groupBuyActivity.getTakeLimitCount())
                .target(groupBuyActivity.getTarget())
                .validTime(groupBuyActivity.getValidTime())
                .status(ActivityStatusEnumVO.valueOf(groupBuyActivity.getStatus()))
                .startTime(groupBuyActivity.getStartTime())
                .endTime(groupBuyActivity.getEndTime())
                .tagId(groupBuyActivity.getTagId())
                .tagScope(groupBuyActivity.getTagScope())
                .build();
    }

    @Override
    public Integer queryOrderCountByActivityId(Long activityId, String userId) {
        GroupBuyOrderList groupBuyOrderListReq = new GroupBuyOrderList();
        groupBuyOrderListReq.setUserId(userId);
        groupBuyOrderListReq.setActivityId(activityId);

        return groupBuyOrderListDao.queryOrderCountByActivityId(groupBuyOrderListReq);
    }

    @Override
    public MarketPayOrderEntity queryMarketPayOrderEntityByOutTradeNo(String userId, String outTradeNo) {

        GroupBuyOrderList groupBuyOrderListReq = new GroupBuyOrderList();
        groupBuyOrderListReq.setUserId(userId);
        groupBuyOrderListReq.setOutTradeNo(outTradeNo);

        GroupBuyOrderList groupBuyOrderListRes = groupBuyOrderListDao.queryGroupBuyOrderRecordByOutTradeNo(groupBuyOrderListReq);
        if(null == groupBuyOrderListRes) return null;

        return MarketPayOrderEntity.builder()
                .teamId(groupBuyOrderListRes.getTeamId())
                .orderId(groupBuyOrderListRes.getOrderId())
                .deductionPrice(groupBuyOrderListRes.getDeductionPrice())
                .tradeOrderStatusEnumVO(TradeOrderStatusEnumVO.valueOf(groupBuyOrderListRes.getStatus()))
                .build();

    }

    @Override
    public GroupBuyTeamEntity queryGroupBuyTeamByTeamId(String teamId) {

        GroupBuyOrder groupBuyOrder = groupBuyOrderDao.queryGroupBuyTeamByTeamId(teamId);

        return GroupBuyTeamEntity.builder()
                .teamId(groupBuyOrder.getTeamId())
                .activityId(groupBuyOrder.getActivityId())
                .targetCount(groupBuyOrder.getTargetCount())
                .completeCount(groupBuyOrder.getCompleteCount())
                .lockCount(groupBuyOrder.getLockCount())
                .status(GroupBuyOrderEnumVO.valueOf(groupBuyOrder.getStatus()))
                .notifyUrl(groupBuyOrder.getNotifyUrl())
                .validStartTime(groupBuyOrder.getValidStartTime())
                .validEndTime(groupBuyOrder.getValidEndTime())
                .build();
    }

    @Transactional(timeout = 500, isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
    @Override
    public boolean settlementMarketPayOrder(GroupBuyTeamSettlementAggregate groupBuyTeamSettlementAggregate) {

        UserEntity userEntity = groupBuyTeamSettlementAggregate.getUserEntity();
        GroupBuyTeamEntity groupBuyTeamEntity = groupBuyTeamSettlementAggregate.getGroupBuyTeamEntity();
        TradePaySuccessEntity tradePaySuccessEntity = groupBuyTeamSettlementAggregate.getTradePaySuccessEntity();

        // 1. 更新拼团订单明细状态
        //把该用户的订单明细（group_buy_order_list）状态从 0→1（已支付）
        GroupBuyOrderList groupBuyOrderListReq = new GroupBuyOrderList();
        // A locking read sees the winner of a concurrent settlement after waiting for its commit.
        GroupBuyOrderList lockedOrder = groupBuyOrderListDao.queryOrderByBusinessKeyForUpdate(GroupBuyOrderList.builder()
                .source(tradePaySuccessEntity.getSource()).channel(tradePaySuccessEntity.getChannel())
                .outTradeNo(tradePaySuccessEntity.getOutTradeNo()).build());
        if (lockedOrder == null || !Objects.equals(lockedOrder.getUserId(), userEntity.getUserId()) ||
                !Objects.equals(lockedOrder.getTeamId(), groupBuyTeamEntity.getTeamId())) throw new AppException(ResponseCode.CHANNEL_MISMATCH);
        if (Objects.equals(lockedOrder.getStatus(), TradeOrderStatusEnumVO.COMPLETE.getCode())) {
            if (lockedOrder.getOutTradeTime() == null || tradePaySuccessEntity.getOutTradeTime() == null ||
                    lockedOrder.getOutTradeTime().getTime() != tradePaySuccessEntity.getOutTradeTime().getTime()) {
                throw new AppException(ResponseCode.PAYMENT_CONFLICT);
            }
            return false;
        }
        if (!Objects.equals(lockedOrder.getStatus(), TradeOrderStatusEnumVO.CREATE.getCode())) throw new AppException(ResponseCode.E0104);
        // 等待行锁后重新区分历史事实与首次支付，避免配置变化阻断成功重放。
        if (isSCBlackIntercept(lockedOrder.getSource(), lockedOrder.getChannel())) throw new AppException(ResponseCode.E0105);
        groupBuyOrderListReq.setOrderId(lockedOrder.getOrderId());
        groupBuyOrderListReq.setSource(lockedOrder.getSource());
        groupBuyOrderListReq.setChannel(lockedOrder.getChannel());
        groupBuyOrderListReq.setUserId(userEntity.getUserId());
        groupBuyOrderListReq.setOutTradeNo(tradePaySuccessEntity.getOutTradeNo());
        groupBuyOrderListReq.setOutTradeTime(tradePaySuccessEntity.getOutTradeTime());
        int updateOrderListStatusCount = groupBuyOrderListDao.updateOrderStatus2COMPLETE(groupBuyOrderListReq);
        if(1 != updateOrderListStatusCount){
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }

        // 2. 更新拼团达成数量（达成人数 +1）
        int updateAddCount = groupBuyOrderDao.updateAddCompleteCount(groupBuyTeamEntity.getTeamId());
        if(1 != updateAddCount){
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }

        // The team's current count, not the rule-chain snapshot, decides completion.
        int updateOrderStatusCount = groupBuyOrderDao.updateOrderStatus2COMPLETE(groupBuyTeamEntity.getTeamId());
        if (1 == updateOrderStatusCount) {

            // 查询拼团交易完成外部单号列表
            List<String> outTradeNoList = groupBuyOrderListDao.queryGroupBuyCompleteOrderOutTradeNoListByTeamId(groupBuyTeamEntity.getTeamId());

            // 拼团完成写入回调任务记录
            NotifyTask notifyTask = new NotifyTask();
            notifyTask.setActivityId(groupBuyTeamEntity.getActivityId());
            notifyTask.setTeamId(groupBuyTeamEntity.getTeamId());
            notifyTask.setNotifyUrl(groupBuyTeamEntity.getNotifyUrl());
            notifyTask.setNotifyCount(0);
            notifyTask.setNotifyStatus(0);
            notifyTask.setParameterJson(JSON.toJSONString(new HashMap<String, Object>() {{
                put("teamId", groupBuyTeamEntity.getTeamId());
                put("outTradeNoList", outTradeNoList);
            }}));

            notifyTaskDao.insert(notifyTask);

            return true;
        }

        return false;
    }

    //判断是否在黑名单
    @Override
    public boolean isSCBlackIntercept(String source, String channel) {
        return dccService.isSCBlackIntercept(source, channel);
    }

    @Override
    public List<NotifyTaskEntity> queryUnExecutedNotifyTaskList() {
        notifyTaskDao.expireExhaustedClaims();
        List<NotifyTask> notifyTaskList = notifyTaskDao.queryUnExecutedNotifyTaskList();
        if (notifyTaskList.isEmpty()) return new ArrayList<>();

        List<NotifyTaskEntity> notifyTaskEntities = new ArrayList<>();
        for (NotifyTask notifyTask : notifyTaskList) {

            NotifyTaskEntity notifyTaskEntity = NotifyTaskEntity.builder()
                    .teamId(notifyTask.getTeamId())
                    .notifyUrl(notifyTask.getNotifyUrl())
                    .notifyCount(notifyTask.getNotifyCount())
                    .parameterJson(notifyTask.getParameterJson())
                    .build();

            notifyTaskEntities.add(notifyTaskEntity);
        }

        return notifyTaskEntities;
    }

    @Override
    public List<NotifyTaskEntity> queryUnExecutedNotifyTaskList(String teamId) {
        notifyTaskDao.expireExhaustedClaims();
        NotifyTask notifyTask = notifyTaskDao.queryUnExecutedNotifyTaskByTeamId(teamId);
        if (null == notifyTask) return new ArrayList<>();
        return Collections.singletonList(NotifyTaskEntity.builder()
                .teamId(notifyTask.getTeamId())
                .notifyUrl(notifyTask.getNotifyUrl())
                .notifyCount(notifyTask.getNotifyCount())
                .parameterJson(notifyTask.getParameterJson())
                .build());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotifyTaskEntity claimNotifyTask(String teamId) {
        // The due check and new lease must use a statement started after any row-lock wait.
        if (notifyTaskDao.lockNotifyTaskByTeamId(teamId) == null) return null;
        NotifyTask claim = NotifyTask.builder().teamId(teamId).claimToken(UUID.randomUUID().toString()).build();
        if (notifyTaskDao.claimNotifyTask(claim) != 1) return null;
        NotifyTask row = notifyTaskDao.queryClaimedNotifyTask(claim);
        return NotifyTaskEntity.builder().teamId(row.getTeamId()).notifyUrl(row.getNotifyUrl())
                .notifyCount(row.getNotifyCount()).parameterJson(row.getParameterJson())
                .claimToken(row.getClaimToken()).build();
    }

    private NotifyTask notificationResult(NotifyTaskEntity task) {
        return NotifyTask.builder().teamId(task.getTeamId()).claimToken(task.getClaimToken())
                .retryDelaySeconds(task.getRetryDelaySeconds()).lastError(task.getLastError()).build();
    }

    private boolean lockNotifyResultRow(NotifyTaskEntity task) {
        // FOR UPDATE may wait for another transaction. The following UPDATE is a new
        // SQL statement, so its NOW(3) is evaluated after the lock has been acquired.
        return notifyTaskDao.lockNotifyTaskByTeamId(task.getTeamId()) != null;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int updateNotifyTaskStatusSuccess(NotifyTaskEntity task) {
        if (!lockNotifyResultRow(task)) return 0;
        return notifyTaskDao.updateNotifyTaskStatusSuccess(notificationResult(task));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int updateNotifyTaskStatusError(NotifyTaskEntity task) {
        if (!lockNotifyResultRow(task)) return 0;
        return notifyTaskDao.updateNotifyTaskStatusError(notificationResult(task));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int updateNotifyTaskStatusRetry(NotifyTaskEntity task) {
        if (!lockNotifyResultRow(task)) return 0;
        return notifyTaskDao.updateNotifyTaskStatusRetry(notificationResult(task));
    }
}
