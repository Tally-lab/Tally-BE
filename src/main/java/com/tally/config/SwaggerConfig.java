package com.tally.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI tallyOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tally API")
                        .description("개인 데이터 분석 및 성능 최적화 시스템 API")
                        .version("1.0.0"));
    }
}