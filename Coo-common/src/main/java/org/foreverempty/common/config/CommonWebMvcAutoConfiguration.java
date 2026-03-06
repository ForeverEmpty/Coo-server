package org.foreverempty.common.config;

import org.foreverempty.common.interceptor.UserInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.web.servlet.HandlerInterceptor")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonWebMvcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(UserInterceptor.class)
    public UserInterceptor userInterceptor() {
        return new UserInterceptor();
    }
}
