package cn.bugstack.test.v2;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
/** Explicit opt-in for a fresh TD01 regression schema; historical default stays unchanged. */
final class V2TestDatabase {
    static HikariDataSource dataSource() {
        String url=System.getenv("PHASE1_TEST_URL");
        if(url==null) url="jdbc:mysql://127.0.0.1:13306/group_buy_market_phase1_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
        else if(!url.matches("jdbc:mysql://[^/]+/group_buy_market_td01_[0-9_]+[?].*")) throw new IllegalStateException("Fresh TD01 regression schema required");
        HikariDataSource ds=new HikariDataSource();ds.setJdbcUrl(url);
        String user=System.getenv("PHASE1_TEST_USER");ds.setUsername(user==null?"root":user);ds.setPassword(System.getenv("PHASE1_DB_PASSWORD"));return ds;
    }
    static void assertIsolated(JdbcTemplate jdbc) {
        String actual=jdbc.queryForObject("select database()",String.class);
        if(System.getenv("PHASE1_TEST_URL")==null) org.junit.Assert.assertEquals("group_buy_market_phase1_test",actual);
        else org.junit.Assert.assertTrue(actual.matches("group_buy_market_td01_[0-9_]+"));
    }
}
