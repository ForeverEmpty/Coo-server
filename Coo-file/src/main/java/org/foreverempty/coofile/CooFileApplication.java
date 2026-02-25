package org.foreverempty.coofile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = { "org.foreverempty.coofile", "org.foreverempty.common" })
public class CooFileApplication {

    public static void main(String[] args) {
        SpringApplication.run(CooFileApplication.class, args);
    }

}
