package cn.bugstack.v2;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name="ab.v2.enabled",havingValue="true")
public class V2WaitingScheduler {
    @Resource private JdbcTemplate jdbc;
    @Resource private V2EventEngine events;

    @Scheduled(initialDelay=10000,fixedDelay=10000)
    public void resume() {
        List<Map<String,Object>> tasks=jdbc.queryForList("select event_id,body,producer from ab_waiting_dependency " +
                "where next_check_at<=now(3) order by next_check_at,event_id limit 50");
        for (Map<String,Object> task:tasks) {
            try { events.process(task.get("body").toString(),task.get("producer").toString()); }
            catch (Exception e) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("v2 waiting event retry failed eventId:{}",task.get("event_id"),e); }
        }
    }
}
