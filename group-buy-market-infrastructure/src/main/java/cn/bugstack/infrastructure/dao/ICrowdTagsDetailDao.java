package cn.bugstack.infrastructure.dao;

import cn.bugstack.infrastructure.dao.po.CrowdTagsDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ICrowdTagsDetailDao {
    void addCrowdTagsUserId(CrowdTagsDetail crowdTagsDetailReq);
    boolean isExactMember(@Param("tagId") String tagId, @Param("userId") String userId);
}
