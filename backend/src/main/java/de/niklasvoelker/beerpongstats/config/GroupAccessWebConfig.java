package de.niklasvoelker.beerpongstats.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class GroupAccessWebConfig implements WebMvcConfigurer {

    private final GroupAccess groupAccess;

    public GroupAccessWebConfig(GroupAccess groupAccess) {
        this.groupAccess = groupAccess;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new GroupAccessInterceptor(groupAccess))
                .addPathPatterns(GroupAccessInterceptor.PATTERN);
    }
}
