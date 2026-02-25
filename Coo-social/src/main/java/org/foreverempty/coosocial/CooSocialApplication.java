package org.foreverempty.coosocial;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
@MapperScan("org.foreverempty.coosocial.mapper")
@ComponentScan(basePackages = {"org.foreverempty.coosocial", "org.foreverempty.common"})
public class CooSocialApplication {

    public static void main(String[] args) {
        SpringApplication.run(CooSocialApplication.class, args);
    }

}
