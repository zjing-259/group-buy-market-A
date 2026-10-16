package cn.bugstack.test.v2;

import cn.bugstack.api.v2.*;
import cn.bugstack.v2.*;
import cn.bugstack.infrastructure.adapter.repository.ActivityRepository;
import cn.bugstack.infrastructure.dcc.DCCService;
import cn.bugstack.domain.activity.service.discount.impl.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.dubbo.config.*;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.apache.ibatis.session.SqlSessionFactory;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import static org.junit.Assert.*;

/** Real TCP Triple/TLS + Redis nonce + MySQL + production repository/engines, no scheduler/MQ worker. */
public class V2TeamDiscoveryLiveTest {
    @Configuration @EnableTransactionManagement @MapperScan("cn.bugstack.infrastructure.dao")
    @Import({ActivityRepository.class,ZJCalculateService.class,MJCalculateService.class,ZKCalculateService.class,NCalculateService.class,
            V2MarketingEngine.class,V2TeamDiscoveryEngine.class,V2TeamDiscoveryRpc.class,V2MarketingRpc.class,
            V2TradeEngine.class,V2TradeRpc.class,V2TeamEngine.class,V2TeamRpc.class,V2EventEngine.class,
            V2PaymentEngine.class,V2PaymentIdentity.class,V2OutboxService.class,V2IntegrationRpc.class,V2Security.class,V2SpringBridge.class})
    public static class Wiring {
        @Bean(destroyMethod="close") public HikariDataSource dataSource(){return new V2TeamDiscoveryTest.Config().dataSource();}
        @Bean public JdbcTemplate jdbcTemplate(DataSource ds){return new JdbcTemplate(ds);}
        @Bean public PlatformTransactionManager transactionManager(DataSource ds){return new DataSourceTransactionManager(ds);}
        @Bean public ObjectMapper objectMapper(){return new ObjectMapper();}
        @Bean public SqlSessionFactory sqlSessionFactory(DataSource ds)throws Exception {
            SqlSessionFactoryBean b=new SqlSessionFactoryBean();b.setDataSource(ds);PathMatchingResourcePatternResolver r=new PathMatchingResourcePatternResolver();
            b.setConfigLocation(r.getResource("classpath:mybatis/config/mybatis-config.xml"));b.setMapperLocations(r.getResources("classpath:mybatis/mapper/*.xml"));return b.getObject();
        }
        @Bean public DCCService dccService(){DCCService d=new DCCService();ReflectionTestUtils.setField(d,"downgradeSwitch","0");ReflectionTestUtils.setField(d,"cutRange","100");ReflectionTestUtils.setField(d,"scBlacklist","");return d;}
        @Bean(destroyMethod="shutdown") public RedissonClient redis(){Config c=new Config();c.useSingleServer().setAddress("redis://td01-redis:6379").setPassword(System.getenv("TD01_REDIS_PASSWORD")).setConnectionMinimumIdleSize(1).setConnectionPoolSize(4);return Redisson.create(c);}
    }
    static AnnotationConfigApplicationContext context;
    static final List<ServiceConfig<?>> exports=new ArrayList<>();
    static final List<ReferenceConfig<?>> references=new ArrayList<>();
    static TeamDiscoveryService discovery;static TradeService trade;static TeamService teams;static MarketingService marketing;static IntegrationService integration;
    static final ObjectMapper JSON=new ObjectMapper();
    static final ApplicationConfig APPLICATION=new ApplicationConfig("td01-live");
    @BeforeClass public static void start() {
        context=new AnnotationConfigApplicationContext();Map<String,Object> properties=new HashMap<>();
        properties.put("ab.v2.enabled",true);properties.put("ab.v2.rpc.client-id","td01-client");properties.put("ab.v2.rpc.key-id","td01-key");
        properties.put("ab.v2.rpc.secret",System.getenv("TD01_RPC_SECRET"));properties.put("ab.v2.rpc.source","td01");properties.put("ab.v2.rpc.channel","web");
        properties.put("ab.v2.rpc.require-tls",true);properties.put("ab.v2.env","td01");
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("td01-private-test",properties));
        context.register(Wiring.class);context.refresh();
        SslConfig ssl=new SslConfig();String cert=System.getenv("TD01_TLS_CERT"),key=System.getenv("TD01_TLS_KEY");
        ssl.setServerKeyCertChainPath(cert);ssl.setServerPrivateKeyPath(key);ssl.setServerTrustCertCollectionPath(cert);
        ssl.setClientKeyCertChainPath(cert);ssl.setClientPrivateKeyPath(key);ssl.setClientTrustCertCollectionPath(cert);
        org.apache.dubbo.rpc.model.ApplicationModel.defaultModel().getApplicationConfigManager().setSsl(ssl);
        export(TeamDiscoveryService.class,V2TeamDiscoveryRpc.class,3000);export(TradeService.class,V2TradeRpc.class,10000);
        export(TeamService.class,V2TeamRpc.class,10000);export(MarketingService.class,V2MarketingRpc.class,10000);export(IntegrationService.class,V2IntegrationRpc.class,10000);
        discovery=reference(TeamDiscoveryService.class);trade=reference(TradeService.class);teams=reference(TeamService.class);marketing=reference(MarketingService.class);integration=reference(IntegrationService.class);
    }
    static <T> void export(Class<T> api,Class<? extends T> impl,int timeout) {
        ServiceConfig<T> s=new ServiceConfig<>();s.setApplication(APPLICATION);s.setRegistry(new RegistryConfig("N/A"));
        ProtocolConfig p=new ProtocolConfig("tri",50051);p.setHost("127.0.0.1");p.setParameters(Collections.singletonMap("ssl-enabled","true"));
        s.setProtocol(p);s.setInterface(api);s.setRef(context.getBean(impl));s.setGroup("gbm-td01");s.setVersion("2.0.0");s.setTimeout(timeout);s.setRetries(0);s.export();exports.add(s);
    }
    static <T> T reference(Class<T> api) {
        ReferenceConfig<T> r=new ReferenceConfig<>();r.setApplication(APPLICATION);r.setScope("remote");r.setInterface(api);r.setUrl("tri://127.0.0.1:50051?ssl-enabled=true");
        r.setGroup("gbm-td01");r.setVersion("2.0.0");r.setRetries(0);r.setTimeout(3000);references.add(r);return r.get();
    }
    @AfterClass public static void stop() {
        for(ReferenceConfig<?> r:references)r.destroy();for(ServiceConfig<?> s:exports)s.unexport();if(context!=null)context.close();
        org.apache.dubbo.rpc.model.ApplicationModel.defaultModel().destroy();
    }
    static Map<String,String> sign(Class<?> api,String method,Object body,String secret) {
        String id=UUID.randomUUID().toString(),timestamp=Long.toString(System.currentTimeMillis());byte[] random=new byte[24];new java.security.SecureRandom().nextBytes(random);
        String nonce=Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String text=String.join("\n","td01-client","td01-key",timestamp,nonce,id,api.getName(),method,"2.0.0","gbm-td01",V2Format.sha256(new V2Security().canonicalJson(body)));
        Map<String,String> a=new HashMap<>();a.put("clientId","td01-client");a.put("keyId","td01-key");a.put("timestamp",timestamp);a.put("nonce",nonce);a.put("requestId",id);a.put("signature",V2Security.hmac(secret,text));return a;
    }
    static <T> RpcResult<T> call(Class<?> api,String method,Object body,Supplier<RpcResult<T>> action) {
        return signed(sign(api,method,body,System.getenv("TD01_RPC_SECRET")),action);
    }
    static <T> RpcResult<T> signed(Map<String,String> attachments,Supplier<RpcResult<T>> action) {
        try{attachments.forEach((k,v)->org.apache.dubbo.rpc.RpcContext.getClientAttachment().setAttachment(k,v));RpcResult<T> r=action.get();assertEquals(attachments.get("requestId"),r.getRequestId());return r;}
        finally{org.apache.dubbo.rpc.RpcContext.getClientAttachment().clearAttachments();}
    }
    static TeamDiscoveryRequest query(){TeamDiscoveryRequest q=new TeamDiscoveryRequest();q.setSource("td01");q.setChannel("web");q.setUserId("viewer");q.setGoodsId("td01-live-goods");q.setActivityId("714201");q.setProductVersion("1");q.setLimit(3);return q;}
    static TeamDiscoveryPage list(TeamDiscoveryRequest q){RpcResult<TeamDiscoveryPage> r=call(TeamDiscoveryService.class,"list",q,()->discovery.list(q));assertEquals("0000",r.getCode());assertNotNull(r.getData());return r.getData();}
    @Test public void actualTlsSignedDiscoveryHasSafeSummaryAndExcludesInvalidCandidates()throws Exception {
        TeamDiscoveryRequest q=query();TeamDiscoveryPage p=list(q);assertEquals(3,p.getItems().size());assertTrue(p.getHasMore());
        assertEquals("td01-live-0000",p.getItems().get(0).getTeamId());
        for(TeamDiscoveryItem i:p.getItems()){assertTrue(i.getTeamId().startsWith("td01-live-"));assertEquals(Integer.valueOf(1),i.getPaidCount());assertEquals(Integer.valueOf(2),i.getReservedCount());}
        String wire=JSON.writeValueAsString(p);assertFalse(wire.contains("outTradeNo"));assertFalse(wire.contains("paymentId"));assertFalse(wire.contains("ownReservation"));
    }
    @Test public void paginationAndExactOwnerWorkAcrossRealSignedCalls(){TeamDiscoveryRequest q=query();q.setLimit(2);TeamDiscoveryPage first=list(q);q.setCursor(first.getNextCursor());assertEquals("td01-live-0002",list(q).getItems().get(0).getTeamId());
        q=query();q.setUserId("CaseOwner");assertEquals("td01-live-0001",list(q).getItems().get(0).getTeamId());q.setUserId("caseowner");assertEquals("td01-live-0000",list(q).getItems().get(0).getTeamId());}
    @Test public void wrongSignatureReplayAndForeignChannelAreRejected(){TeamDiscoveryRequest q=query();
        assertEquals("AUTH_INVALID",signed(sign(TeamDiscoveryService.class,"list",q,"incorrect"),()->discovery.list(q)).getCode());
        Map<String,String> a=sign(TeamDiscoveryService.class,"list",q,System.getenv("TD01_RPC_SECRET"));assertEquals("0000",signed(a,()->discovery.list(q)).getCode());assertEquals("REPLAY_REJECTED",signed(a,()->discovery.list(q)).getCode());
        q.setChannel("other");assertEquals("FORBIDDEN",call(TeamDiscoveryService.class,"list",q,()->discovery.list(q)).getCode());}
    @Test public void signatureBindsInterfaceAndMethodNotJustBody(){TeamDiscoveryRequest q=query();
        assertEquals("AUTH_INVALID",signed(sign(TeamService.class,"query",q,System.getenv("TD01_RPC_SECRET")),()->discovery.list(q)).getCode());}
    @Test public void invalidCursorAndVersionChangeAreExplicitFailures(){TeamDiscoveryRequest q=query();String c=list(q).getNextCursor();q.setCursor(c);q.setProductVersion("2");assertEquals("TEAM_DISCOVERY_CONTEXT_CHANGED",call(TeamDiscoveryService.class,"list",q,()->discovery.list(q)).getCode());
        q.setProductVersion("1");q.setCursor(c+"=");assertEquals("0002",call(TeamDiscoveryService.class,"list",q,()->discovery.list(q)).getCode());}
    @Test public void realRepositoryEnforcesHiddenTagQualification(){TeamDiscoveryRequest q=query();q.setGoodsId("td01-tag-goods");q.setActivityId("714202");assertEquals("E0110",call(TeamDiscoveryService.class,"list",q,()->discovery.list(q)).getCode());
        q.setUserId("eligible");assertEquals(0,list(q).getItems().size());}
    @Test public void plaintextCannotReachRpcFrames()throws Exception {try(java.net.Socket s=new java.net.Socket("127.0.0.1",50051)){s.setSoTimeout(2000);s.getOutputStream().write("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII));assertEquals(-1,s.getInputStream().read());}}
    @Test public void untrustedTlsCertificateCannotCompleteHandshake()throws Exception {
        try(javax.net.ssl.SSLSocket s=(javax.net.ssl.SSLSocket)javax.net.ssl.SSLSocketFactory.getDefault().createSocket("127.0.0.1",50051)) {
            s.setSoTimeout(2000);try{s.startHandshake();fail("Untrusted test certificate accepted");}catch(javax.net.ssl.SSLHandshakeException expected){assertNotNull(expected.getMessage());}
        }
    }
    @Test public void databaseRuntimeCannotDdlOrReadOriginalSchema(){JdbcTemplate db=context.getBean(JdbcTemplate.class);
        for(String sql:Arrays.asList("CREATE TABLE td01_forbidden (id int)","SELECT count(*) FROM group_buy_market_b06.ab_team","SELECT count(*) FROM mysql.user")) {
            try{db.execute(sql);fail("Runtime privilege expanded");}catch(org.springframework.dao.DataAccessException expected){assertNotNull(expected.getCause());}
        }}
    @Test public void oldRpcReservePaymentQueryCloseAndReconciliationRemainCompatible(){
        ReserveRequest q=new ReserveRequest();q.setSource("td01");q.setChannel("web");q.setUserId("flow-owner");q.setOutTradeNo("td01-live-flow-paid");q.setGoodsId("td01-flow-goods");q.setActivityId("714203");q.setProductVersion("1");q.setQuantity(1);q.setExpectedPayPrice("90.00");q.setCurrency("CNY");
        RpcResult<Reservation> reserve=call(TradeService.class,"reserve",q,()->trade.reserve(q));assertEquals("0000",reserve.getCode());String team=reserve.getData().getTeamId();
        assertEquals(team,call(TradeService.class,"reserve",q,()->trade.reserve(q)).getData().getTeamId());
        TeamDiscoveryRequest d=query();d.setGoodsId("td01-flow-goods");d.setActivityId("714203");assertTrue(list(d).getItems().isEmpty());
        Map<String,Object> event=new LinkedHashMap<>();String paidAt=V2Format.now();event.put("eventId","td01-live-event-paid");event.put("eventType","PAYMENT_SUCCEEDED");event.put("schemaVersion",1);event.put("producer","td01-client");event.put("occurredAt",paidAt);event.put("source","td01");event.put("channel","web");event.put("correlationId",null);
        Map<String,Object> payload=new LinkedHashMap<>();payload.put("userId","flow-owner");payload.put("outTradeNo",q.getOutTradeNo());payload.put("paymentId","td01-live-payment");payload.put("paidAmount","90.00");payload.put("currency","CNY");payload.put("paidAt",paidAt);event.put("payload",payload);
        try{assertEquals("APPLIED",context.getBean(V2EventEngine.class).process(JSON.writeValueAsString(event),"td01-client").getOutcome());}catch(Exception e){throw new AssertionError(e);}
        TradeQueryRequest read=new TradeQueryRequest();read.setSource("td01");read.setChannel("web");read.setUserId(q.getUserId());read.setOutTradeNo(q.getOutTradeNo());assertEquals("PAID",call(TradeService.class,"query",read,()->trade.query(read)).getData().getOrderStatus());
        assertEquals(team,list(d).getItems().get(0).getTeamId());
        q.setUserId("flow-unpaid");q.setOutTradeNo("td01-live-flow-unpaid");q.setTeamId(team);assertEquals("0000",call(TradeService.class,"reserve",q,()->trade.reserve(q)).getCode());
        CloseRequest close=new CloseRequest();close.setSource("td01");close.setChannel("web");close.setUserId(q.getUserId());close.setOutTradeNo(q.getOutTradeNo());close.setCloseId("td01-live-close");close.setReason("USER_CANCEL");close.setPaymentDisposition("NEVER_OPENED");close.setVerifiedAt(V2Format.now());assertEquals("0000",call(TradeService.class,"close",close,()->trade.close(close)).getCode());
        context.getBean(V2TeamEngine.class).expire("td01-reconcile-expired");ReconciliationQueryRequest rq=new ReconciliationQueryRequest();rq.setSource("td01");rq.setChannel("web");rq.setTeamId("td01-reconcile-expired");RpcResult<ReconciliationSnapshot> rec=call(TeamService.class,"queryReconciliation",rq,()->teams.queryReconciliation(rq));assertEquals("0000",rec.getCode());
        ReconciliationCompleteRequest done=new ReconciliationCompleteRequest();done.setSource("td01");done.setChannel("web");done.setTeamId(rq.getTeamId());done.setReconciliationId(rec.getData().getReconciliationId());done.setRosterVersion(rec.getData().getRosterVersion());done.setCompletionId("td01-live-completion");done.setCheckedAt(V2Format.now());done.setResults(Collections.emptyList());assertEquals("FAILED",call(TeamService.class,"completeReconciliation",done,()->teams.completeReconciliation(done)).getData().getTeamStatus());
        TeamQueryRequest tq=new TeamQueryRequest();tq.setSource("td01");tq.setChannel("web");tq.setUserId("viewer");tq.setTeamId(team);assertEquals("FORMING",call(TeamService.class,"query",tq,()->teams.query(tq)).getData().getTeamStatus());
    }
    @Test public void oneThousandCandidateRpcLatencyIsBounded()throws Exception {List<Long> times=new ArrayList<>();TeamDiscoveryRequest q=query();q.setLimit(10);list(q);for(int i=0;i<20;i++){long start=System.nanoTime();assertEquals(10,list(q).getItems().size());times.add((System.nanoTime()-start)/1000000);}
        Collections.sort(times);Map<String,Object> metrics=new LinkedHashMap<>();metrics.put("samples",20);metrics.put("concurrency",1);metrics.put("candidateCount",1000);metrics.put("interferenceCount",2000);metrics.put("rpcP95Millis",times.get(18));metrics.put("rpcMaxMillis",times.get(19));metrics.put("runtime","Java8 Docker Linux/amd64 real TLS/Triple, local MySQL");
        Files.write(Paths.get(System.getenv("TD01_METRICS")),JSON.writeValueAsBytes(metrics));assertTrue("RPC p95="+times.get(18),times.get(18)<1000);}
    @Test public void repeatedSignedQueriesDoNotWriteAnyMysqlBusinessTable()throws Exception {
        String before=snapshot();for(int i=0;i<10;i++){TeamDiscoveryRequest q=query();TeamDiscoveryPage first=list(q);q.setCursor(first.getNextCursor());list(q);}assertEquals(before,snapshot());
    }
    static String snapshot()throws Exception {
        JdbcTemplate jdbc=context.getBean(JdbcTemplate.class);StringBuilder rows=new StringBuilder();
        for(String table:jdbc.queryForList("select table_name from information_schema.tables where table_schema=database() and table_type='BASE TABLE' order by table_name",String.class)) {
            assertTrue(table.matches("[a-z0-9_]+"));List<String> values=new ArrayList<>();for(Map<String,Object> row:jdbc.queryForList("select * from `"+table+"`"))values.add(JSON.writeValueAsString(row));Collections.sort(values);rows.append(table).append(values);
        }
        return V2Format.sha256(rows.toString());
    }
}
