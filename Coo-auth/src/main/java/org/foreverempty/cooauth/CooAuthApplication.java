package org.foreverempty.cooauth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

@SpringBootApplication
@EnableDiscoveryClient
@EnableElasticsearchRepositories(basePackages = "org.foreverempty.cooauth.es.repository")
@MapperScan("org.foreverempty.cooauth.mapper")
@ComponentScan(basePackages = { "org.foreverempty.cooauth", "org.foreverempty.common" })
public class CooAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(CooAuthApplication.class, args);
    }

}
