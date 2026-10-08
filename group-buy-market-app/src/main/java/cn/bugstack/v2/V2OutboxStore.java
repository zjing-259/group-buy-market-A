package cn.bugstack.v2;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.util.*;

@Service
public class V2OutboxStore {
    @Resource private JdbcTemplate jdbc;
    public static final class Claim {
        public String eventId,topic,eventType,source,channel,body,token;
        public int attempts;
    }

    public List<String> dueIds() {
        return jdbc.query("select event_id from ab_outbox where " +
                "((status='PENDING' and next_attempt_at<=now(3)) or " +
                "(status='CLAIMED' and lease_until<=now(3))) order by next_attempt_at,event_id limit 50",
                (rs,n)->rs.getString(1));
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Claim claim(String id) {
        List<Map<String,Object>> rows=jdbc.queryForList("select * from ab_outbox where event_id=? for update",id);
        if (rows.isEmpty()) return null;
        Map<String,Object> row=rows.get(0);
        String status=row.get("status").toString();
        java.sql.Timestamp now=jdbc.queryForObject("select now(3)",java.sql.Timestamp.class);
        if (!(("PENDING".equals(status) && !now.before((Date)row.get("next_attempt_at"))) ||
                ("CLAIMED".equals(status) && row.get("lease_until")!=null && !now.before((Date)row.get("lease_until")))))
            return null;
        Claim c=new Claim(); c.eventId=id; c.topic=row.get("topic").toString();
        c.eventType=row.get("event_type").toString(); c.source=row.get("source").toString();
        c.channel=row.get("channel").toString(); c.body=row.get("body").toString();
        c.token=UUID.randomUUID().toString(); c.attempts=((Number)row.get("attempts")).intValue()+1;
        jdbc.update("update ab_outbox set status='CLAIMED',claim_token=?,attempts=?," +
                "lease_until=date_add(now(3),interval 60 second) where event_id=?",c.token,c.attempts,id);
        return c;
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean published(Claim c) {
        if (jdbc.queryForList("select event_id from ab_outbox where event_id=? for update",c.eventId).isEmpty()) return false;
        return jdbc.update("update ab_outbox set status='PUBLISHED',published_at=now(3),claim_token=null," +
                        "lease_until=null,last_error=null where event_id=? and status='CLAIMED' and claim_token=? " +
                        "and lease_until>now(3)",c.eventId,c.token)==1;
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean failed(Claim c,String error) {
        if (jdbc.queryForList("select event_id from ab_outbox where event_id=? for update",c.eventId).isEmpty()) return false;
        int seconds=c.attempts==1?60:c.attempts==2?120:c.attempts==3?240:480;
        return jdbc.update("update ab_outbox set status='PENDING',next_attempt_at=date_add(now(3),interval ? second)," +
                        "claim_token=null,lease_until=null,last_error=? where event_id=? and status='CLAIMED' " +
                        "and claim_token=? and lease_until>now(3)",seconds,error,c.eventId,c.token)==1;
    }
}
