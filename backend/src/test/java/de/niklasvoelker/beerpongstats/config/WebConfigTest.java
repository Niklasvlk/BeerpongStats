package de.niklasvoelker.beerpongstats.config;

import de.niklasvoelker.beerpongstats.api.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.resource.CachingResourceResolver;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WebMvcTest(controllers = ApiExceptionHandler.class)
@Import(WebConfig.class)
class WebConfigTest {

    @Autowired
    private HandlerMapping resourceHandlerMapping;

    // Die Interceptor-Konfiguration für die Gruppen wird im Slice mitgeladen und braucht diese Abhängigkeit
    @MockitoBean
    private GroupAccess groupAccess;

    @Test
    void spaFallbackIsNotCached() {
        SimpleUrlHandlerMapping mapping = assertInstanceOf(SimpleUrlHandlerMapping.class, resourceHandlerMapping);
        ResourceHttpRequestHandler handler = assertInstanceOf(ResourceHttpRequestHandler.class,
                mapping.getUrlMap().get("/**"));

        // Ein Cache würde für jeden unbekannten Pfad (Fallback auf index.html) einen Eintrag anlegen
        assertTrue(handler.getResourceResolvers().stream().noneMatch(CachingResourceResolver.class::isInstance));
        assertInstanceOf(SpaResourceResolver.class, handler.getResourceResolvers().getFirst());
        assertEquals(1, handler.getResourceResolvers().size());
    }
}
