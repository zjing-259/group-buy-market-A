package cn.bugstack.config;

import cn.bugstack.types.annotations.DCCValue;
import cn.bugstack.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;
import java.util.Map;

//使用Redis来完成动态配置中心（DCC）的开发。
@Slf4j
@Configuration
public class DCCValueBeanFactory implements BeanPostProcessor {
    private static final String BATH_CONFIG_PATH = "group_buy_market_dcc_";

    private final RedissonClient redissonClient;

    private final Map<String, Object> dccObject = new java.util.concurrent.ConcurrentHashMap<>();

    public DCCValueBeanFactory(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Bean("dccTopic")
    public RTopic dccRedisTopicListener(){
        RTopic topic = redissonClient.getTopic("group_buy_market_dcc");

        topic.addListener(String.class, (charSequence, s) -> {
            String[] split = s.split(Constants.SPLIT, 2);
            if (split.length != 2) return;

            String attribute = split[0];
            String key = BATH_CONFIG_PATH + attribute;
            String value = split[1];
            if ("cutRange".equals(attribute)) {
                try { cn.bugstack.types.common.CutRangePolicy.parse(value); }
                catch (IllegalArgumentException e) { log.warn("Ignoring invalid cutRange configuration"); return; }
            }

            RBucket<Object> bucket = redissonClient.getBucket(key);

            if(!bucket.isExists()) return;
            bucket.set(value);

            Object objBean = dccObject.get(key);
            if(null == objBean) return;

            Class<?> objBeanClass = objBean.getClass();
            if(AopUtils.isAopProxy(objBeanClass)){
                objBeanClass = AopUtils.getTargetClass(objBean);
            }

            try {
                Field field = objBeanClass.getDeclaredField(attribute);
                field.setAccessible(true);
                field.set(objBean, value);
                field.setAccessible(false);
            }catch (Exception e){
                throw new RuntimeException("...");
            }
        });

        return topic;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetBeanClass = bean.getClass();
        Object targetBeanObject = bean;

        if(AopUtils.isAopProxy(bean)){
            targetBeanClass = AopUtils.getTargetClass(bean);
            targetBeanObject = AopProxyUtils.getSingletonTarget(bean);
        }

        Field[] fields = targetBeanClass.getDeclaredFields();
        for(Field field:fields){
            //筛选出被@DCCValue 注解标记的字段
            if(!field.isAnnotationPresent(DCCValue.class)){
                continue;
            }

            DCCValue dccValue = field.getAnnotation(DCCValue.class);

            String value = dccValue.value();

            if(StringUtils.isBlank(value)){
                throw new RuntimeException("...");
            }

            String[] split = value.split(":");
            String key = BATH_CONFIG_PATH.concat(split[0]);
            String defaultValue = split.length == 2 ? split[1] : null;

            String setValue = defaultValue;

            try{
                if(StringUtils.isBlank(defaultValue)){
                    throw new RuntimeException("...");
                }

                RBucket<String> bucket = redissonClient.getBucket(key);
                if(!bucket.isExists()){
                    bucket.set(defaultValue);
                }else {
                    setValue = bucket.get();
                }
                if ("cutRange".equals(split[0])) {
                    try { cn.bugstack.types.common.CutRangePolicy.parse(setValue); }
                    catch (IllegalArgumentException e) {
                        log.warn("Invalid stored cutRange, restoring configured default");
                        setValue = defaultValue;
                        bucket.set(defaultValue);
                    }
                }

                field.setAccessible(true);
                field.set(targetBeanObject, setValue);
                field.setAccessible(false);

            }catch (Exception e){
                throw new RuntimeException("...");
            }

            dccObject.put(key, targetBeanObject);

        }

        return bean;
    }
}
