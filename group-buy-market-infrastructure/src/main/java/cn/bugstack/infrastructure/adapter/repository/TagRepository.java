package cn.bugstack.infrastructure.adapter.repository;

import cn.bugstack.domain.tag.adapter.repository.ITagRepository;
import cn.bugstack.domain.tag.model.entity.CrowTagsJobEntity;
import cn.bugstack.infrastructure.dao.ICrowdTagsDao;
import cn.bugstack.infrastructure.dao.ICrowdTagsDetailDao;
import cn.bugstack.infrastructure.dao.ICrowdTagsJobDao;
import cn.bugstack.infrastructure.dao.po.CrowdTags;
import cn.bugstack.infrastructure.dao.po.CrowdTagsDetail;
import cn.bugstack.infrastructure.dao.po.CrowdTagsJob;
import cn.bugstack.infrastructure.redis.IRedisService;
import com.thoughtworks.xstream.converters.reflection.AbstractReflectionConverter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBitSet;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
@Slf4j
@Repository
public class TagRepository implements ITagRepository {
    @Resource
    private ICrowdTagsDao crowdTagsDao;
    @Resource
    private ICrowdTagsDetailDao crowdTagsDetailDao;
    @Resource
    private ICrowdTagsJobDao crowdTagsJobDao;
    @Resource
    private IRedisService redisService;

    @Override
    public CrowTagsJobEntity queryCrowTagsJobEntity(String tagId, String batchId) {
        CrowdTagsJob crowdTagsJobReq = new CrowdTagsJob();
        crowdTagsJobReq.setTagId(tagId);
        crowdTagsJobReq.setBatchId(batchId);

        CrowdTagsJob crowdTagsJobRes = crowdTagsJobDao.queryCrowdTagsJob(crowdTagsJobReq);

        if (crowdTagsJobRes == null) {
            log.warn("未找到标签任务配置, tagId={}, batchId={}", tagId, batchId);
            return null;
        }

        //转换查询到的结果并返回
        return CrowTagsJobEntity.builder()
                .tagType(crowdTagsJobRes.getTagType())
                .tagRule(crowdTagsJobRes.getTagRule())
                .statStartTime(crowdTagsJobRes.getStatStartTime())
                .statEndTime(crowdTagsJobRes.getStatEndTime())
                .build();
    }

    @Override
    public void addCrowTagsUserId(String tagId, String userId) {
        CrowdTagsDetail crowdTagsDetailReq = new CrowdTagsDetail();
        crowdTagsDetailReq.setTagId(tagId);
        crowdTagsDetailReq.setUserId(userId);

        //唯一索引异常
        try {
            crowdTagsDetailDao.addCrowdTagsUserId(crowdTagsDetailReq);

            RBitSet bitSet = redisService.getBitSet(tagId);
            bitSet.set(redisService.getIndexFromUserId(userId));
        } catch (DuplicateKeyException ignore) {
        }

    }

    @Override
    public void updateCrowTagsStatistics(String tagId, int size) {
        CrowdTags crowdTagsReq = new CrowdTags();
        crowdTagsReq.setTagId(tagId);
        crowdTagsReq.setStatistics(size);

        crowdTagsDao.updateCrowdTagsStatistics(crowdTagsReq);
    }
}
