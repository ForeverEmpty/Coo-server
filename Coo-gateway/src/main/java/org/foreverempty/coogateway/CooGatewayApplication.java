package org.foreverempty.coogateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
@EnableDiscoveryClient
@ComponentScan(basePackages = { "org.foreverempty.coogateway", "org.foreverempty.common" })
public class CooGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(CooGatewayApplication.class, args);
    }

}
