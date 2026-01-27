package org.foreverempty.coochat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = {"org.foreverempty.coochat", "org.foreverempty.common"})
public class CooChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(CooChatApplication.class, args);
    }

}
