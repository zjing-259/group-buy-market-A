package cn.bugstack.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class NotifyRequestDTO {
    /** 组队ID */
    private String teamId;
    /** 外部单号列表 */
    private List<String> outTradeNoList;

}
