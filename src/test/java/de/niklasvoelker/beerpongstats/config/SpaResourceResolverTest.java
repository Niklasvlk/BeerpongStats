package de.niklasvoelker.beerpongstats.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SpaResourceResolverTest {

    @TempDir
    Path staticDir;

    private final SpaResourceResolver resolver = new SpaResourceResolver();

    private Resource location() {
        return new FileSystemResource(staticDir.toString() + "/");
    }

    private Resource resolve(String path) {
        return resolver.resolveResource(null, path, List.of(location()), null);
    }

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(staticDir.resolve("index.html"), "<app-root>");
        Files.writeString(staticDir.resolve("main-ABC.js"), "js");
    }

    @Test
    void existingFileIsServedAsIs() throws IOException {
        assertEquals("main-ABC.js", resolve("main-ABC.js").getFilename());
    }

    @Test
    void rootServesIndex() {
        assertEquals("index.html", resolve("index.html").getFilename());
    }

    @Test
    void unknownClientRouteFallsBackToIndex() {
        assertEquals("index.html", resolve("players/3").getFilename());
        assertEquals("index.html", resolve("newMatch").getFilename());
    }

    @Test
    void unknownApiPathIsNotFound() {
        assertNull(resolve("api/unknown"));
    }

    @Test
    void withoutBuiltFrontendNothingIsServed() throws IOException {
        Files.delete(staticDir.resolve("index.html"));
        assertNull(resolve("players"));
    }
}
