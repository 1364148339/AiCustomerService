package com.aimacrodroid.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc (Swagger 3) 配置
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AiMacroDroid API")
                        .version("1.0.0")
                        .description("AiMacroDroid Server 后端接口文档")
                        .contact(new Contact().name("Admin")));
    }
}
