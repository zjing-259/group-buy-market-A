package cn.bugstack.infrastructure.gateway;

import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 该类是 DDD 六边形架构里的出站适配器/网关
 * 不写任何业务，只是"发请求 → 拿结果"
 * 2026/9/2 14:48
 * @author 正经
 */
@Slf4j
@Service
public class GroupBuyNotifyService {

    @Resource
    private OkHttpClient okHttpClient;

    public String groupBuyNotify(String apiUrl, String notifyRequestDTOJSON) throws Exception {
        try {
            // 1. 构建参数
            MediaType mediaType = MediaType.parse("application/json");
            RequestBody body = RequestBody.create(mediaType, notifyRequestDTOJSON);
            Request request = new Request.Builder()
                    .url(apiUrl)
                    .post(body)
                    .addHeader("content-type", "application/json")
                    .build();

            // 2. 调用接口
            Response response = okHttpClient.newCall(request).execute();

            // 3. 返回结果
            return response.body().string();
        } catch (Exception e) {
            log.error("拼团回调 HTTP 接口服务异常 {}", apiUrl, e);
            throw new AppException(ResponseCode.HTTP_EXCEPTION);
        }
    }
}
