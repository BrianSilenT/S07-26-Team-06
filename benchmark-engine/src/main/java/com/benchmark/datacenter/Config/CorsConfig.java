package com.benchmark.datacenter.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Habilita CORS para que el frontend (corriendo en otro puerto durante
 * desarrollo, ej. Vite en :5173) pueda llamar a esta API en :8080.
 *
 * TODO produccion: restringir allowedOrigins al dominio real del front
 * una vez desplegado, en vez de la lista de puertos de desarrollo local.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(
                        "http://localhost:5173",  // Vite default
                        "http://localhost:3000",  // Create React App default
                        "http://127.0.0.1:5173"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
