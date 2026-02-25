package org.foreverempty.coogateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"org.foreverempty.coogateway", "org.foreverempty.common"})
public class CooGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(CooGatewayApplication.class, args);
    }

}
