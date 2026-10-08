package cn.bugstack.infrastructure.dcc;


import cn.bugstack.types.annotations.DCCValue;
import cn.bugstack.types.common.Constants;
import org.springframework.stereotype.Service;

import cn.bugstack.types.common.CutRangePolicy;
import java.util.Arrays;
import java.util.List;

@Service
public class DCCService {
    @DCCValue("downgradeSwitch:0")
    private String downgradeSwitch;
    @DCCValue("cutRange:100")//默认全部通过
    private volatile String cutRange;
    @DCCValue("scBlacklist:s02c02")//默认黑名单s02c02
    private String scBlacklist;

    public boolean isdowngradeSwitch(){
        return "1".equals(downgradeSwitch);
    }

    public boolean iscutRange(String userId){
        int percent;
        try { percent = CutRangePolicy.parse(cutRange); }
        catch (IllegalArgumentException e) { return false; }
        return Math.floorMod(userId.hashCode(), 100) < percent;
    }

    public boolean isSCBlackIntercept(String source, String channel){
        //list里面装黑名单
        List<String> list = Arrays.asList(scBlacklist.split(Constants.SPLIT));
        //判断传入的SC值是否在黑名单里
        return list.contains(source + channel);
    }

}
