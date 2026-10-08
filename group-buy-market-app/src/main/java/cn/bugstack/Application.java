package cn.bugstack;

import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;

@SpringBootApplication
@Configurable
@EnableScheduling //开启定时任务
@EnableDubbo(scanBasePackages = "cn.bugstack.v2")
public class Application {

    public static void main(String[] args){
        SpringApplication.run(Application.class, args);
    }

}
