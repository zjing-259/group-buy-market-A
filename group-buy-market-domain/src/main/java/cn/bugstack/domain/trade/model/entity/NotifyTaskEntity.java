package cn.bugstack.domain.trade.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
//回调任务的实体
public class NotifyTaskEntity {
    /**
     * 拼单组队ID
     */
    private String teamId;
    /**
     * 回调接口
     */
    private String notifyUrl;
    /**
     * 已尝试发送次数
     */
    private Integer notifyCount;
    /**
     * 参数对象
     */
    private String parameterJson;

    public String lockKey() {
        return "notify_job_lock_key_" + this.teamId;
    }

}
