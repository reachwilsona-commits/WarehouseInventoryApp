package com.company.inventory.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Class for Open API configuration and documentation.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI warehouseInventoryOpenAPI(SecurityProperties securityProperties) {
        final String schemeName = "ApiKeyAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Warehouse Inventory Reservation Service")
                        .version("1.0.0")
                        .description("Real-time inventory reservation service. All endpoints except /health "
                                + "require an X-API-Key header."))
                .components(new Components()
                        .addSecuritySchemes(schemeName, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(securityProperties.headerName())))
                .addSecurityItem(new SecurityRequirement().addList(schemeName));
    }
}
