package com.websocket.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class LargePayloadConfig implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // Configure JSON converter for large payloads
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
        jsonConverter.setDefaultCharset(java.nio.charset.StandardCharsets.UTF_8);
        converters.add(jsonConverter);
    }

    @Bean
    public org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory tomcatFactory() {
        return new org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory() {
            @Override
            protected void postProcessContext(org.apache.catalina.Context context) {
                // Configure Tomcat for large payloads
                context.setSwallowOutput(true);
            }
        };
    }
}
