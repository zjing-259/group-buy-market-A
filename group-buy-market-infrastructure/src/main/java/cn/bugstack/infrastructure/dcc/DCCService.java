package cn.bugstack.infrastructure.dcc;


import cn.bugstack.types.annotations.DCCValue;
import cn.bugstack.types.common.Constants;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class DCCService {
    @DCCValue("downgradeSwitch:0")
    private String downgradeSwitch;
    @DCCValue("cutRange:100")//默认全部通过
    private String cutRange;
    @DCCValue("scBlacklist:s02c02")//默认黑名单s02c02
    private String scBlacklist;

    public boolean isdowngradeSwitch(){
        return "1".equals(downgradeSwitch);
    }

    public boolean iscutRange(String userId){
        int hashCode = Math.abs(userId.hashCode());
        int lastTwoDigits = hashCode % 100;

        return lastTwoDigits <= Integer.parseInt(cutRange);
    }

    public boolean isSCBlackIntercept(String source, String channel){
        //list里面装黑名单
        List<String> list = Arrays.asList(scBlacklist.split(Constants.SPLIT));
        //判断传入的SC值是否在黑名单里
        return list.contains(source + channel);
    }

}
