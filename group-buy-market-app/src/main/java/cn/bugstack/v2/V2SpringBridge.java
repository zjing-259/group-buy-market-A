package cn.bugstack.v2;

import cn.bugstack.api.v2.V2AuthenticationException;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class V2SpringBridge implements ApplicationContextAware {
    private static volatile ApplicationContext context;
    @Override public void setApplicationContext(ApplicationContext value) throws BeansException { context=value; }
    public static <T> T get(Class<T> type) {
        ApplicationContext current=context;
        if (current==null) throw new V2AuthenticationException("AUTH_REQUIRED");
        return current.getBean(type);
    }
}
