package cn.bugstack.infrastructure.gateway;

import okhttp3.*;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Service
public class GroupBuyNotifyService {
    @Resource private OkHttpClient okHttpClient;

    public String groupBuyNotify(String apiUrl, String payload) throws Exception {
        OkHttpClient client = okHttpClient.newBuilder().callTimeout(10, TimeUnit.SECONDS)
                .connectTimeout(3, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false)
                .retryOnConnectionFailure(false).build();
        Request request = new Request.Builder().url(apiUrl)
                .post(RequestBody.create(MediaType.parse("application/json"), payload)).build();
        try (Response response = client.newCall(request).execute()) {
            // Only the exact protocol acknowledgement on HTTP 200 confirms delivery.
            if (response.code() != 200) return "HTTP_STATUS_" + response.code();
            if (response.body() == null) return "EMPTY_RESPONSE_BODY";
            return "success".equals(response.body().string()) ? "success" : "UNEXPECTED_RESPONSE_BODY";
        }
    }
}
