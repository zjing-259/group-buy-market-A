package cn.bugstack.v2;

import cn.bugstack.api.v2.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import javax.annotation.Resource;
import java.sql.Timestamp;
import java.util.*;

@Service
public class V2TeamDiscoveryEngine {
    @Resource private JdbcTemplate jdbc;
    @Resource private V2MarketingEngine marketing;

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ, timeout=2, rollbackFor=Exception.class)
    public TeamDiscoveryPage list(TeamDiscoveryRequest q) {
        if (q==null) throw new V2Failure(V2Codes.INVALID_PARAMETER);
        V2TeamDiscoveryCursor.id(q.getSource(),8); V2TeamDiscoveryCursor.id(q.getChannel(),8);
        V2TeamDiscoveryCursor.id(q.getUserId(),64); V2TeamDiscoveryCursor.id(q.getGoodsId(),64);
        V2Format.activityId(q.getActivityId()); V2Format.positiveVersion(q.getProductVersion());
        if (q.getLimit()==null || q.getLimit()<1 || q.getLimit()>10) throw new V2Failure(V2Codes.INVALID_PARAMETER);
        V2TeamDiscoveryCursor cursor=V2TeamDiscoveryCursor.decode(q);
        Timestamp now=jdbc.queryForObject("select now(3)",Timestamp.class);
        TrialRequest trial=new TrialRequest(); trial.setSource(q.getSource()); trial.setChannel(q.getChannel());
        trial.setUserId(q.getUserId()); trial.setGoodsId(q.getGoodsId()); trial.setActivityId(q.getActivityId());
        trial.setProductVersion(q.getProductVersion()); trial.setQuantity(1);
        V2MarketingEngine.Quote quote=marketing.quote(trial,false,now);
        if (quote==null) throw new V2Failure("E0101");
        if (!quote.visible || !quote.eligible) throw new V2Failure("E0110");
        List<Object> args=new ArrayList<>(Arrays.asList(q.getSource(),q.getChannel(),q.getGoodsId(),
                Long.parseLong(q.getActivityId()),now,now,quote.offer.getTargetCount(),q.getSource(),q.getChannel(),q.getUserId(),q.getUserId()));
        String sql="select t.team_id,t.activity_id,t.goods_id,t.team_version,t.team_status,t.target_count,"+
                "t.reserved_count,t.paid_count,t.valid_start_time,t.valid_end_time from ab_team t force index (idx_ab_team_discovery) " +
                "where t.source=? and t.channel=? and t.goods_id=? and t.activity_id=? " +
                "and t.team_status='FORMING' and t.valid_start_time<=? and t.valid_end_time>? " +
                "and t.target_count=? and t.paid_count>=1 and t.paid_count<=t.reserved_count " +
                "and t.reserved_count<t.target_count and t.target_count<=100 " +
                "and not exists (select 1 from ab_trade_order o force index (idx_ab_order_discovery_owner) where o.team_id=t.team_id " +
                "and o.source=? and o.channel=? and o.user_id=? and binary o.user_id=binary ? " +
                "and o.record_kind='RESERVATION' and o.order_status<>'CANCELLED') ";
        if (cursor!=null) {
            sql+="and (t.valid_end_time>? or (t.valid_end_time=? and t.team_id>?)) ";
            args.add(cursor.endTime); args.add(cursor.endTime); args.add(cursor.teamId);
        }
        sql+="order by t.valid_end_time,t.team_id limit ?"; args.add(q.getLimit()+1);
        final List<Object> parameters=args; final String query=sql;
        List<TeamDiscoveryItem> items=jdbc.query(connection -> {
            java.sql.PreparedStatement statement=connection.prepareStatement(query);
            statement.setQueryTimeout(2);
            for (int i=0;i<parameters.size();i++) statement.setObject(i+1,parameters.get(i));
            return statement;
        },(rs,n)-> {
            TeamDiscoveryItem item=new TeamDiscoveryItem(); item.setTeamId(rs.getString("team_id"));
            item.setActivityId(rs.getString("activity_id")); item.setGoodsId(rs.getString("goods_id"));
            item.setTeamVersion(rs.getString("team_version")); item.setTeamStatus(rs.getString("team_status"));
            item.setTargetCount(rs.getInt("target_count")); item.setReservedCount(rs.getInt("reserved_count"));
            item.setPaidCount(rs.getInt("paid_count")); item.setValidStartTime(V2Format.utc(rs.getTimestamp("valid_start_time")));
            item.setValidEndTime(V2Format.utc(rs.getTimestamp("valid_end_time"))); return item;
        });
        boolean more=items.size()>q.getLimit(); if (more) items=new ArrayList<>(items.subList(0,q.getLimit()));
        TeamDiscoveryPage page=new TeamDiscoveryPage(); page.setSource(q.getSource()); page.setChannel(q.getChannel());
        page.setUserId(q.getUserId()); page.setGoodsId(q.getGoodsId()); page.setActivityId(q.getActivityId());
        page.setProductVersion(q.getProductVersion()); page.setServerTime(V2Format.utc(now)); page.setItems(items);
        page.setHasMore(more); page.setNextCursor(more ? V2TeamDiscoveryCursor.encode(q,
                items.get(items.size()-1).getValidEndTime(),items.get(items.size()-1).getTeamId()) : null);
        return page;
    }
}
