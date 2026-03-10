package org.foreverempty.coochat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@ComponentScan(basePackages = {"org.foreverempty.coochat", "org.foreverempty.common"})
public class CooChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(CooChatApplication.class, args);
    }

}
