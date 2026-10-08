package cn.bugstack.trigger.http;

import cn.bugstack.api.IDCCService;
import cn.bugstack.api.response.Response;
import cn.bugstack.types.enums.ResponseCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/gbm/dcc/")
public class DCCController implements IDCCService {
    @Resource
    private RTopic dccTopic;

    /*
    * 动态值变更
    * <p>
    * curl http://127.0.0.1:8091/api/v1/gbm/dcc/update_config?key=downgradeSwitch&value=1
    * curl http://127.0.0.1:8091/api/v1/gbm/dcc/update_config?key=cutRange&value=0
    * */

    @RequestMapping(value = "update_config", method = RequestMethod.GET)
    @Override
    public Response<Boolean> updateConfig(@RequestParam String key, @RequestParam String value) {
        try{
            if ("cutRange".equals(key)) cn.bugstack.types.common.CutRangePolicy.parse(value);
            dccTopic.publish(key + "," + value);

            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo()).data(true)
                    .build();
        }catch (IllegalArgumentException e){
            return Response.<Boolean>builder().code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(ResponseCode.ILLEGAL_PARAMETER.getInfo()).data(false).build();
        }catch (Exception e){
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }
}
