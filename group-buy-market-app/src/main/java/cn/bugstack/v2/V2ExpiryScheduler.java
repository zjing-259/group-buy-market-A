package cn.bugstack.v2;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.util.List;

@Component
@ConditionalOnProperty(name="ab.v2.enabled",havingValue="true")
public class V2ExpiryScheduler {
    @Resource private JdbcTemplate jdbc;
    @Resource private V2TeamEngine teams;

    @Scheduled(fixedDelayString="${ab.v2.expiry-scan-ms:5000}")
    public void scan() {
        List<String> ids=jdbc.query("select team_id from ab_team where team_status='FORMING' " +
                "and valid_end_time<=now(3) order by valid_end_time,team_id limit 50",(rs,n)->rs.getString(1));
        for (String id:ids) {
            try { teams.expire(id); }
            catch (Exception e) { org.slf4j.LoggerFactory.getLogger(getClass()).error("v2 team expiry failed: {}",id,e); }
        }
    }
}
