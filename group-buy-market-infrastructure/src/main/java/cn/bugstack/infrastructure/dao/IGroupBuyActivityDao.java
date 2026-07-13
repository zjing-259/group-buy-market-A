package cn.bugstack.infrastructure.dao;

import cn.bugstack.infrastructure.dao.po.GroupBuyActivity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IGroupBuyActivityDao {
    List<GroupBuyActivity> queryGroupBuyActivityList();

    GroupBuyActivity queryVaildGroupBuyActivity(GroupBuyActivity groupBuyActivityReq);

    GroupBuyActivity queryVaildGroupBuyActivityId(Long activityId);

    GroupBuyActivity queryGroupBuyActivityByActivityId(Long activityId);
}