package cn.bugstack.api;

import cn.bugstack.api.response.Response;

public interface IDCCService {

    Response<Boolean> updateConfig(String key, String value);

}
