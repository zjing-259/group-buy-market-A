"""TD01 real isolated MySQL/JUnit/Triple TLS validation, no original container mutation.

Requires a freshly compiled A package/test classes and an existing healthy B06
MySQL. Creates new TD01 schemas, a private temporary Redis/network and bounded
Java8 test containers. Original B06 business rows/images are checked and kept.
"""
import argparse,csv,hashlib,importlib.util,json,os,re,secrets,shutil,subprocess,sys,time,xml.etree.ElementTree as ET
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
DOCKER=['docker','--context','desktop-linux']


def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest().upper()
def load(p):return json.loads(p.read_text(encoding='utf-8-sig'))


def main(args):
    if not re.fullmatch(r'td01_[0-9]{8}_[0-9]{2}',args.run_name):raise ValueError('Fresh td01_YYYYMMDD_NN run required')
    name=args.run_name;work=ROOT/'target/team-discovery'/name;work.mkdir(parents=True,exist_ok=False)
    private=ROOT/'docs/dev-ops/ab-v2/.local'/name;private.mkdir(parents=True,exist_ok=False)
    if os.name=='nt':
        identity=next(csv.reader([subprocess.check_output(['whoami','/user','/fo','csv','/nh'],text=True).strip()]))[1]
        r=subprocess.run(['icacls',str(private),'/inheritance:r','/grant:r','*'+identity+':(OI)(CI)F','*S-1-5-18:(OI)(CI)F','*S-1-5-32-544:(OI)(CI)F'],capture_output=True)
        if r.returncode:raise RuntimeError('Private ACL failed')
    helper=args.b_root/'docs/tests/prepare-td00-fixture.py';s=importlib.util.spec_from_file_location('td00_helper',helper);db=importlib.util.module_from_spec(s);s.loader.exec_module(db)
    secrets_used=[]
    def run(label,cmd,**kw):
        r=subprocess.run([str(x) for x in cmd],capture_output=True,timeout=kw.pop('timeout',180),**kw)
        out=r.stdout if isinstance(r.stdout,str) else r.stdout.decode('utf-8',errors='replace')
        err=r.stderr if isinstance(r.stderr,str) else r.stderr.decode('utf-8',errors='replace')
        text=out+err
        for secret in secrets_used:text=text.replace(secret,'[REDACTED]')
        (work/(label+'.log')).write_text(text,encoding='utf-8')
        if r.returncode:raise RuntimeError(label+' failed; see ignored log '+str(work/(label+'.log')))
        print('PASS '+label,flush=True);return out
    summary={'stage':'TD-01','runName':name,'status':'RUNNING','checks':{}}
    def save():(work/'report.json').write_text(json.dumps(summary,indent=2)+'\n')
    save();owned=[];network='zj-'+name.replace('_','-')
    original=load(args.b_root/'docs/tests/td00-acceptance.json')
    protected=original['originalFinancialTables'];before={d:db.fingerprint(d,t) for d,t in protected.items()}
    schema_names=['group_buy_market_'+name+'_'+str(i) for i in [1,2,3]]
    values=load(args.deployed_root/'.local/b06/runtime/private/values.json');secrets_used.extend(str(x) for x in values.values())
    runtime_users=[]
    try:
        rows=load(args.b_root/'docs/b06-final-release.json')['allRuntimeImages']
        observed=json.loads(run('original-container-images',DOCKER+['inspect']+['zj-market-b06-'+n+'-1' for n in rows]))
        summary['checks']['originalPinnedImages']=all(r['Image']==rows[r['Config']['Labels']['com.docker.compose.service']] for r in observed)
        # The full inspect is private/ignored; public report only contains this boolean.
        for i,schema in enumerate(schema_names):
            assert db.sql(f"SELECT count(*) FROM information_schema.schemata WHERE schema_name='{schema}';").strip()=='0'
            db.clone('group_buy_market_b06',schema)
            migration=ROOT/'docs/dev-ops/mysql/sql/migrations/20261014-team-discovery.sql'
            db.sql(f'USE `{schema}`;\n'+migration.read_text(encoding='utf-8'))
            user=name.replace('_','')+'_'+str(i);password=secrets.token_urlsafe(24);secrets_used.append(password)
            db.sql(f"CREATE USER '{user}'@'%' IDENTIFIED BY '{password}' REQUIRE SSL;")
            for table in db.tables(schema):
                if table=='b06_init_journal':continue
                db.sql(f"GRANT SELECT,INSERT,UPDATE,DELETE ON `{schema}`.`{table}` TO '{user}'@'%';")
            runtime_users.append((user,password));summary['checks']['indexes-'+str(i)]=db.sql(f"SELECT count(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema='{schema}' AND index_name IN ('idx_ab_team_discovery','idx_ab_order_discovery_owner');").strip()=='2'
        summary['schemas']=schema_names;summary['migrationSha256']=sha(migration)
        trust=private/'mysql-trust.p12';shutil.copyfile(args.deployed_root/'.local/b06/runtime/mysql-trust.p12',trust)
        run('certificate',['C:/Program Files/Java/jdk-21/bin/java.exe',ROOT/'docs/tests/ab-v2-client/CreateTestCertificate.java',private/'tls'])
        redis_secret=secrets.token_urlsafe(24);rpc_secret=secrets.token_urlsafe(32);secrets_used.extend([redis_secret,rpc_secret])
        # Build a portable exact test classpath from Maven's resolved dependency list.
        tests=ET.parse(ROOT/'group-buy-market-app/target/surefire-reports/TEST-cn.bugstack.test.v2.V2AuthorizationTest.xml')
        cp=next(x.get('value') for x in tests.findall('./properties/property') if x.get('name')=='java.class.path')
        libs=work/'lib';libs.mkdir();classes=work/'classes';classes.mkdir();test_classes=work/'test-classes';test_classes.mkdir()
        for entry in cp.split(os.pathsep):
            if not entry.strip():continue
            p=Path(entry)
            if p.is_file() and p.suffix=='.jar':shutil.copyfile(p,libs/p.name)
            elif p.is_dir() and p.name in ['classes','test-classes'] and p.parent.name=='target' and ROOT in p.resolve().parents:
                shutil.copytree(p,test_classes if p.name=='test-classes' else classes,dirs_exist_ok=True)
            else:raise RuntimeError('Unexpected resolved classpath entry')
        dest=work/'docs/schemas';dest.mkdir(parents=True);shutil.copyfile(ROOT/'docs/schemas/team-discovery-v1.examples.json',dest/'team-discovery-v1.examples.json')
        (work/'group-buy-market-app').mkdir()
        shutil.copyfile(ROOT/'group-buy-market-app/target/group-buy-market-app.jar',work/'tested-app.jar')
        summary['appJarSha256']=sha(work/'tested-app.jar');summary['contractJarSha256']=sha(ROOT/'group-buy-market-contract/target/group-buy-market-contract-1.1.0-td.1.jar')
        assert summary['contractJarSha256']==load(args.b_root/'docs/team-discovery-contract-lock.json')['sha256']
        (private/'logback.xml').write_text('<configuration><appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender"><encoder><pattern>%level %logger{24} - %msg%n</pattern></encoder></appender><root level="WARN"><appender-ref ref="CONSOLE"/></root></configuration>')
        images=load(args.b_root/'deploy/b06/images.lock.json')
        run('network-create',DOCKER+['network','create','--label','td01.owner='+name,network])
        redis='zj-'+name.replace('_','-')+'-redis';owned.append(redis)
        redisenv=private/'redis.env';redisenv.write_text('TD01_REDIS_PASSWORD='+redis_secret+'\n')
        run('redis-create',DOCKER+['run','-d','--name',redis,'--label','td01.owner='+name,'--network',network,'--network-alias','td01-redis','--memory','64m','--env-file',redisenv,images['redis'],'sh','-c','exec redis-server --requirepass "$TD01_REDIS_PASSWORD" --save "" --appendonly no'])
        def jvm(label,schema_index,suites):
            user,password=runtime_users[schema_index]
            url='jdbc:mysql://mysql:3306/'+schema_names[schema_index]+'?serverTimezone=UTC&sslMode=VERIFY_IDENTITY&trustCertificateKeyStoreUrl=file:/private/mysql-trust.p12&trustCertificateKeyStoreType=PKCS12&trustCertificateKeyStorePassword='+str(values['truststore'])+'&connectTimeout=3000&socketTimeout=6000'
            env=private/(label+'.env');env.write_text('\n'.join(['TD01_JDBC_URL='+url,'TD01_DB_USER='+user,'TD01_DB_PASSWORD='+password,'PHASE1_TEST_URL='+url,'PHASE1_TEST_USER='+user,'PHASE1_DB_PASSWORD='+password,'TD01_REDIS_PASSWORD='+redis_secret,'TD01_RPC_SECRET='+rpc_secret,'TD01_TLS_CERT=/private/tls/certificate.pem','TD01_TLS_KEY=/private/tls/private-key.pem','TD01_METRICS=/work/live-metrics.json'])+'\n')
            container='zj-'+name.replace('_','-')+'-'+label;owned.append(container)
            run(label+'-create',DOCKER+['create','--name',container,'--label','td01.owner='+name,'--memory','768m','--network','zj-market-b06_data','--env-file',env,'--mount','type=bind,src='+str(private)+',dst=/private,readonly','--mount','type=bind,src='+str(work)+',dst=/work','-w','/work/group-buy-market-app','--entrypoint','java',images['java8'],'-XX:ActiveProcessorCount=2','-Xmx384m','-Dlogback.configurationFile=/private/logback.xml','-cp','/work/test-classes:/work/classes:/work/lib/*','org.junit.runner.JUnitCore']+suites)
            run(label+'-connect',DOCKER+['network','connect',network,container])
            run(label,DOCKER+['start','-a',container],timeout=240)
            exit_code=int(run(label+'-exit',DOCKER+['inspect','--format','{{.State.ExitCode}}',container]).strip())
            if exit_code:raise RuntimeError(label+' JUnit failed; see log')
            summary['checks'][label]=True;save()
        jvm('discovery-unit',0,['cn.bugstack.test.v2.V2TeamDiscoveryTest','cn.bugstack.test.v2.V2TeamDiscoveryCursorTest'])
        jvm('legacy-v2',1,['cn.bugstack.test.v2.'+n for n in ['V2PaymentFlowTest','V2ProductEventTest','V2MarketingContractTest','V2OutboxRecoveryTest','V2AuthorizationTest','V2TlsGateTest','V2SecurityVectorTest','V2LegacyHttpGuardTest']])
        live=schema_names[2]
        seed=[f'USE `{live}`; START TRANSACTION;']
        for goods,activity,tag,scope,target in [('td01-live-goods',714201,None,None,3),('td01-tag-goods',714202,'td01-members','1,2',3),('td01-flow-goods',714203,None,None,2)]:
            discount=str(activity+10000000)
            seed+=[f"INSERT INTO group_buy_discount(discount_id,discount_name,discount_desc,discount_type,market_plan,market_expr) VALUES ('{discount}','TD01','TD01',0,'ZJ','10');",
                   f"INSERT INTO group_buy_activity(activity_id,activity_name,discount_id,group_type,take_limit_count,target,valid_time,status,start_time,end_time,tag_id,tag_scope) VALUES ({activity},'TD01','{discount}',1,100,{target},120,1,UTC_TIMESTAMP()-INTERVAL 1 DAY,UTC_TIMESTAMP()+INTERVAL 30 DAY,{repr(tag) if tag else 'NULL'},{repr(scope) if scope else 'NULL'});",
                   f"INSERT INTO sc_sku_activity(source,channel,activity_id,goods_id) VALUES ('td01','web',{activity},'{goods}');",
                   f"INSERT INTO ab_product_projection VALUES ('td01','web','{goods}',1,'TD01',100.00,'CNY','ON_SALE','td01',UTC_TIMESTAMP(3));"]
        seed += ["INSERT INTO crowd_tags(tag_id,tag_name,tag_desc,statistics) VALUES ('td01-members','TD01','TD01',1);", "INSERT INTO crowd_tags_detail(tag_id,user_id) VALUES ('td01-members','eligible');", "SET @end=UTC_TIMESTAMP(3)+INTERVAL 2 HOUR;"]
        for i in range(3000):
            goods='td01-live-goods' if i<1000 else 'td01-interference';team='td01-live-'+str(i).zfill(4)
            end='@end' if i<1000 else '@end-INTERVAL 30 MINUTE'
            seed.append(f"INSERT INTO ab_team(team_id,source,channel,activity_id,goods_id,target_count,reserved_count,paid_count,team_status,team_version,valid_start_time,valid_end_time) VALUES ('{team}','td01','web',714201,'{goods}',3,2,1,'FORMING',1,UTC_TIMESTAMP()-INTERVAL 1 HOUR,{end});")
        for team,reserved,paid,state,end in [('unpaid',1,0,'FORMING','@end'),('full',3,1,'FORMING','@end'),('expired',2,1,'FORMING','UTC_TIMESTAMP()-INTERVAL 1 SECOND'),('reconciling',2,1,'RECONCILING','@end'),('completed',3,3,'COMPLETED','@end'),('failed',2,1,'FAILED','@end')]:
            seed.append(f"INSERT INTO ab_team(team_id,source,channel,activity_id,goods_id,target_count,reserved_count,paid_count,team_status,team_version,valid_start_time,valid_end_time) VALUES ('td01-{team}','td01','web',714201,'td01-live-goods',3,{reserved},{paid},'{state}',1,UTC_TIMESTAMP()-INTERVAL 1 HOUR,{end});")
        seed += ["INSERT INTO ab_team(team_id,source,channel,activity_id,goods_id,target_count,reserved_count,paid_count,team_status,team_version,valid_start_time,valid_end_time) VALUES ('td01-reconcile-expired','td01','web',714203,'td01-flow-goods',2,0,0,'FORMING',1,UTC_TIMESTAMP()-INTERVAL 1 HOUR,UTC_TIMESTAMP()-INTERVAL 1 SECOND);",
                 "INSERT INTO ab_trade_order(source,channel,out_trade_no,record_kind,user_id,team_id,order_status,record_version) VALUES ('td01','web','td01-case-order','RESERVATION','CaseOwner','td01-live-0000','RESERVED',1);",'COMMIT;']
        db.sql('\n'.join(seed))
        db.sql(f'USE `{live}`; ANALYZE TABLE ab_team,ab_trade_order;')
        # Explain the exact frozen filter/order shape with the dataset used by the live RPC.
        query="SELECT t.team_id,t.activity_id,t.goods_id,t.team_version,t.team_status,t.target_count,t.reserved_count,t.paid_count,t.valid_start_time,t.valid_end_time FROM ab_team t FORCE INDEX (idx_ab_team_discovery) WHERE t.source='td01' AND t.channel='web' AND t.goods_id='td01-live-goods' AND t.activity_id=714201 AND t.team_status='FORMING' AND t.valid_start_time<=now(3) AND t.valid_end_time>now(3) AND t.target_count=3 AND t.paid_count>=1 AND t.paid_count<=t.reserved_count AND t.reserved_count<t.target_count AND t.target_count<=100 AND NOT EXISTS (SELECT 1 FROM ab_trade_order o FORCE INDEX (idx_ab_order_discovery_owner) WHERE o.team_id=t.team_id AND o.source='td01' AND o.channel='web' AND o.user_id='viewer' AND BINARY o.user_id=BINARY 'viewer' AND o.record_kind='RESERVATION' AND o.order_status<>'CANCELLED') ORDER BY t.valid_end_time,t.team_id LIMIT 11"
        explain=db.sql(f'USE `{live}`; EXPLAIN '+query+';');(work/'explain.txt').write_text(explain)
        analysis=db.sql(f'USE `{live}`; EXPLAIN ANALYZE '+query+';');(work/'explain-analyze.txt').write_text(analysis)
        summary['checks']['discovery-index-used']='using idx_ab_team_discovery' in analysis
        summary['checks']['owner-index-used']='using idx_ab_order_discovery_owner' in analysis
        sql_times=[]
        for _ in range(20):
            sample=db.sql(f'USE `{live}`; EXPLAIN ANALYZE '+query+';')
            sql_times.append(float(re.search(r'actual time=[0-9.]+\.\.([0-9.]+)',sample).group(1)))
        summary['sqlTiming']={'samples':20,'concurrency':1,'measurement':'MySQL EXPLAIN ANALYZE root iterator total actual milliseconds','p95Millis':sorted(sql_times)[18],'maxMillis':max(sql_times)}
        summary['checks']['sql-p95-under-200ms']=summary['sqlTiming']['p95Millis']<=200
        jvm('live-tls',2,['cn.bugstack.test.v2.V2TeamDiscoveryLiveTest'])
        summary['latency']=load(work/'live-metrics.json')
        after={d:db.fingerprint(d,t) for d,t in protected.items()};summary['originalFinancialBefore']=before;summary['originalFinancialAfter']=after;summary['checks']['originalFinancialUnchanged']=before==after
        summary['checks']['contractStillFrozen']=summary['contractJarSha256']==load(args.b_root/'docs/team-discovery-contract-lock.json')['sha256']
        assert all(summary['checks'].values()),'A required check failed; preserve evidence'
        summary['status']='PASSED_A_DISCOVERY_REAL_MYSQL_TLS';save()
    except Exception as e:
        summary['status']='FAILED';summary['error']=str(e);save();raise
    finally:
        # Only containers/networks created and ownership-checked by this run; schemas/logs retained.
        for container in reversed(owned):
            r=subprocess.run(DOCKER+['inspect','--format','{{index .Config.Labels "td01.owner"}}',container],capture_output=True,text=True)
            if r.returncode==0 and r.stdout.strip()==name:run('cleanup-'+container,DOCKER+['rm','-f',container])
        r=subprocess.run(DOCKER+['network','inspect','--format','{{index .Labels "td01.owner"}}',network],capture_output=True,text=True)
        if r.returncode==0 and r.stdout.strip()==name:run('cleanup-network',DOCKER+['network','rm',network])
    print(json.dumps({'status':summary['status'],'checks':len(summary['checks']),'report':str(work/'report.json')}))


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--run-name',required=True)
    p.add_argument('--b-root',type=Path,default=Path('D:/Java/zj-market'));p.add_argument('--deployed-root',type=Path,default=Path('D:/Java/zj-market-b-06'))
    main(p.parse_args())
