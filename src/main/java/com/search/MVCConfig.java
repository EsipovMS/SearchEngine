package com.search;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@org.springframework.context.annotation.Configuration
public class MVCConfig implements WebMvcConfigurer {
    @Bean
    public Logger getLogger() {
        return LogManager.getRootLogger();
    }
}
