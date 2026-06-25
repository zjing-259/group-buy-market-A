package cn.bugstack.domain.tag.service;

import cn.bugstack.domain.tag.adapter.repository.ITagRepository;
import cn.bugstack.domain.tag.model.entity.CrowTagsJobEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class TagService implements ITagService{
    @Resource
    private ITagRepository repository;

    @Override
    public void execTagBatchJob(String tagId, String batchId) {
        log.info("人群标签批次任务 tagId:{} batchId:{}", tagId, batchId);
        //查询任务
        CrowTagsJobEntity crowTagsJobEntity = repository.queryCrowTagsJobEntity(tagId, batchId);
        //采集用户数据
        //暂时自定义两个用户
        List<String> userList = Arrays.asList("wh", "zj", "xiaofuge", "lierqou");//不用改userList
        for (String userId : userList) {
            repository.addCrowTagsUserId(tagId, userId);
        }

        //更新人群标签统计量
        repository.updateCrowTagsStatistics(tagId, userList.size());
    }
}
