package cn.bugstack.test.domain.tag;

import cn.bugstack.domain.tag.service.ITagService;
import cn.bugstack.infrastructure.redis.IRedisService;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.redisson.api.RBitSet;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class ITagServiceTest {

    @Resource
    ITagService tagService;
    @Resource
    IRedisService redisService;
    /*@Test
    public void test_tag_job(){
        tagService.execTagBatchJob("RQ_KJHKL98UU78H66554GFDV","10001");
    }

    @Test
    public void test_get_job_bitmap(){
        int offsetWh = redisService.getIndexFromUserId("wh");
        int offsetZj = redisService.getIndexFromUserId("zj");
        int offsetLx = redisService.getIndexFromUserId("lx");
        // 必须使用已声明的变量
        log.info("offset: wh={}, zj={}, lx={}", offsetWh, offsetZj, offsetLx);

        RBitSet bitSet = redisService.getBitSet("RQ_KJHKL98UU78H66554GFDV");
        log.info("测试结果：wh={}, zj={}, lx={}", bitSet.get(offsetWh), bitSet.get(offsetZj), bitSet.get(offsetLx));

    }*/
    @Test
    public void test_tag_job_and_verify() {
        // 写入
        tagService.execTagBatchJob("RQ_KJHKL98UU78H66554GFDV", "10001");

        // 立即验证
        RBitSet bitSet = redisService.getBitSet("RQ_KJHKL98UU78H66554GFDV");
        log.info("wh={}, zj={}, lx={}",
                bitSet.get(redisService.getIndexFromUserId("wh")),
                bitSet.get(redisService.getIndexFromUserId("zj")),
                bitSet.get(redisService.getIndexFromUserId("lx")));
    }

}
