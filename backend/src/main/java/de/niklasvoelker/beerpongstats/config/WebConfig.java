package de.niklasvoelker.beerpongstats.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                // Kein Cache: der Fallback liefert für jeden unbekannten Pfad index.html, ein Cache würde unbegrenzt wachsen
                .resourceChain(false)
                .addResolver(new SpaResourceResolver());
    }
}
