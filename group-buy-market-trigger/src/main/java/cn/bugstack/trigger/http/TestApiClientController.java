package cn.bugstack.trigger.http;

import cn.bugstack.api.dto.NotifyRequestDTO;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/test/")
//模拟第三方回调接收服务，用于验证拼团完成后的通知回调流程。
public class TestApiClientController {
    /**
     * 模拟回调案例
     *
     * @param notifyRequestDTO 通知回调参数
     * @return success 成功，error 失败
     */
    //@RequestMapping(value = "group_buy_notify", method = RequestMethod.POST)
    @PostMapping("group_buy_notify")
    public String groupBuyNotify(@RequestBody NotifyRequestDTO notifyRequestDTO) {
        log.info("模拟测试第三方服务接收拼团回调 {}", JSON.toJSONString(notifyRequestDTO));

        return "success";
    }
}
