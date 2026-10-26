package de.niklasvoelker.beerpongstats.config;

import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Liefert statische Dateien aus, fällt für unbekannte Pfade aber auf die index.html der Angular-App zurück,
 * damit Deep-Links wie /players/3 beim Neuladen funktionieren. /api bleibt davon ausgenommen (echtes 404).
 */
public class SpaResourceResolver extends PathResourceResolver {

    @Override
    protected Resource getResource(String resourcePath, Resource location) throws IOException {
        Resource requested = location.createRelative(resourcePath);
        if (requested.exists() && requested.isReadable()) {
            return requested;
        }
        if (resourcePath.startsWith("api/")) {
            return null;
        }
        Resource index = location.createRelative("index.html");
        return index.exists() && index.isReadable() ? index : null;
    }
}
