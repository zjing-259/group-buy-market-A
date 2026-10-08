package cn.bugstack.v2;

import cn.bugstack.api.v2.*;
import cn.bugstack.domain.activity.adapter.repository.IActivityRepository;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.service.discount.IDiscountCalculateService;
import cn.bugstack.infrastructure.dcc.DCCService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class V2MarketingEngine {
    @Resource private JdbcTemplate jdbc;
    @Resource private IActivityRepository activityRepository;
    @Resource private Map<String, IDiscountCalculateService> discounts;
    @Resource private DCCService dcc;

    public static final class Quote {
        public GroupBuyActivityDiscountVO activity;
        public Offer offer;
        public boolean visible;
        public boolean eligible;
    }

    public Quote quote(TrialRequest request, boolean lockProduct) {
        if (request==null) throw new V2Failure(V2Codes.INVALID_PARAMETER);
        V2Format.id(request.getSource(), 8); V2Format.id(request.getChannel(), 8);
        V2Format.id(request.getUserId(), 64); V2Format.id(request.getGoodsId(), 64);
        if (!Integer.valueOf(1).equals(request.getQuantity())) throw new V2Failure(V2Codes.INVALID_PARAMETER);
        long version = V2Format.positiveVersion(request.getProductVersion());
        if (request.getActivityId() != null) V2Format.activityId(request.getActivityId());
        if (dcc.isdowngradeSwitch()) throw new V2Failure("E0003");
        if (!dcc.iscutRange(request.getUserId())) throw new V2Failure("E0004");
        if (dcc.isSCBlackIntercept(request.getSource(),request.getChannel())) throw new V2Failure("E0105");

        String productSql = "select product_version,original_price,sale_status from ab_product_projection " +
                "where source=? and channel=? and goods_id=?" + (lockProduct ? " for update" : "");
        List<Map<String,Object>> products = jdbc.queryForList(productSql, request.getSource(), request.getChannel(), request.getGoodsId());
        if (products.isEmpty()) throw new V2Failure(V2Codes.PRODUCT_NOT_FOUND);
        Map<String,Object> product = products.get(0);
        if (((Number)product.get("product_version")).longValue() != version)
            throw new V2Failure(V2Codes.PRODUCT_VERSION_MISMATCH);
        if (!"ON_SALE".equals(product.get("sale_status"))) throw new V2Failure(V2Codes.PRODUCT_NOT_SALEABLE);

        List<Long> mapped = jdbc.query("select activity_id from sc_sku_activity where source=? and channel=? and goods_id=? " +
                "and binary source=binary ? and binary channel=binary ? and binary goods_id=binary ?", 
                (rs, n) -> rs.getLong(1), request.getSource(), request.getChannel(), request.getGoodsId(),
                request.getSource(), request.getChannel(), request.getGoodsId());
        if (mapped.isEmpty()) {
            if (request.getActivityId() != null) throw new V2Failure("E0108");
            return null;
        }
        Long activityId = mapped.get(0);
        if (request.getActivityId() != null && activityId.longValue() != Long.parseLong(request.getActivityId()))
            throw new V2Failure("E0108");
        GroupBuyActivityDiscountVO activity = activityRepository.queryGroupBuyActivityDiscountVO(activityId);
        if (activity == null) {
            if (request.getActivityId() != null) throw new V2Failure("E0101");
            return null;
        }
        java.sql.Timestamp now=jdbc.queryForObject("select now(3)",java.sql.Timestamp.class);
        if (!Integer.valueOf(1).equals(activity.getStatus())) {
            if (request.getActivityId()!=null) throw new V2Failure("E0101");
            return null;
        }
        if (activity.getStartTime()==null || activity.getEndTime()==null) throw new V2Failure("E0111");
        if (now.before(activity.getStartTime()) || !now.before(activity.getEndTime())) {
            if (request.getActivityId()!=null) throw new V2Failure("E0102");
            return null;
        }
        if (activity.getTarget() == null || activity.getTarget() < 1 || activity.getTarget() > 100 ||
                activity.getValidTime() == null || activity.getValidTime() <= 0)
            throw new V2Failure("E0111");
        boolean member = activity.getTagId() != null && !activity.getTagId().isEmpty() &&
                activityRepository.isTagCrowRange(activity.getTagId(), request.getUserId());
        boolean visible = activity.getTagId() == null || activity.getTagId().isEmpty() || activity.isVisible() || member;
        boolean eligible = activity.getTagId() == null || activity.getTagId().isEmpty() || activity.isEnable() || member;
        BigDecimal original = (BigDecimal) product.get("original_price");
        GroupBuyActivityDiscountVO.GroupBuyDiscount discount = activity.getGroupBuyDiscount();
        IDiscountCalculateService calculator = discounts.get(discount.getMarketPlan());
        if (calculator == null) throw new V2Failure("E0001");
        BigDecimal pay = calculator.calculate(request.getUserId(), original, discount);
        if (pay.compareTo(new BigDecimal("0.01")) < 0 || pay.compareTo(original) > 0) throw new V2Failure("E0111");
        Offer offer = new Offer();
        offer.setActivityId(Long.toString(activityId)); offer.setGoodsId(request.getGoodsId());
        offer.setOriginalPrice(V2Format.money(original)); offer.setPayPrice(V2Format.money(pay));
        offer.setDeductionPrice(V2Format.money(original.subtract(pay))); offer.setCurrency("CNY");
        offer.setTargetCount(activity.getTarget()); offer.setProductVersion(Long.toString(version));
        Quote result = new Quote(); result.activity = activity; result.offer = offer;
        result.visible = visible; result.eligible = eligible;
        return result;
    }

    public TrialResponse trial(TrialRequest request) {
        Quote quote = quote(request, false);
        TrialResponse result = new TrialResponse();
        result.setQuotedAt(V2Format.now());
        result.setAvailable(quote != null);
        result.setVisible(quote != null && quote.visible);
        result.setEligible(quote != null && quote.eligible);
        result.setReasonCode(quote == null ? "NO_ACTIVITY" : quote.eligible ? null : "E0110");
        result.setOffer(quote != null && quote.visible ? quote.offer : null);
        return result;
    }
}
