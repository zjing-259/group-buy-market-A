package cn.bugstack.infrastructure.dao;

import cn.bugstack.infrastructure.dao.po.NotifyTask;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface INotifyTaskDao {
    void insert(NotifyTask notifyTask);

    List<NotifyTask> queryUnExecutedNotifyTaskList();

    NotifyTask queryUnExecutedNotifyTaskByTeamId(String teamId);

    int claimNotifyTask(NotifyTask task);
    NotifyTask queryClaimedNotifyTask(NotifyTask task);
    String lockNotifyTaskByTeamId(String teamId);
    int expireExhaustedClaims();

    int updateNotifyTaskStatusSuccess(NotifyTask task);

    int updateNotifyTaskStatusError(NotifyTask task);

    int updateNotifyTaskStatusRetry(NotifyTask task);

}
