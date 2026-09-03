package cn.bugstack.domain.tag.adapter.repository;

import cn.bugstack.domain.tag.model.entity.CrowdTagsJobEntity;

public interface ITagRepository {
    CrowdTagsJobEntity queryCrowTagsJobEntity(String tagId, String batchId);

    void addCrowTagsUserId(String tagId, String userId);

    void updateCrowTagsStatistics(String tagId, int size);

}
