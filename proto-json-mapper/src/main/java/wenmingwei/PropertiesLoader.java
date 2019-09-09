package wenmingwei;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

@Slf4j
class PropertiesLoader {

    Properties loadProperties(String propertyKey, String defaultUri) throws IOException {
        String propertiesURI = System.getProperty(propertyKey, defaultUri);

        if (log.isDebugEnabled()) {
            log.debug("Loading properties with uri {}, before parse uri, might fail back to default uri: {}", propertiesURI, defaultUri);
        }

        URI uri;
        try {
            uri = new URI(propertiesURI);
        } catch (URISyntaxException ex) {
            log.error("Failed to parse uri: {}, fail back to default one: {}", propertiesURI, defaultUri, ex);
            uri = URI.create(defaultUri);
        }

        if (log.isDebugEnabled()) {
            log.debug("Loading properties with uri {}", uri);
        }

        String scheme = uri.getScheme();
        String path = uri.getPath();

        if (scheme == null) {
            throw new IllegalArgumentException("Scheme of uri(" + propertiesURI + ") is NULL.");
        }

        if (path == null) {
            throw new IllegalArgumentException("Path of uri(" + propertiesURI + ") is NULL.");
        }

        Properties properties = new Properties();
        if ("classpath".equalsIgnoreCase(scheme)) {
            if (log.isDebugEnabled()) {
                log.debug("Loading from classpath.({})", uri);
            }
            while (path.startsWith("/")) {
                path = path.substring(1);
            }
            path = path.trim();
            Preconditions.checkArgument(!Strings.isNullOrEmpty(path), "Path is empty(" + propertiesURI + ")");
            try (
                    InputStreamReader reader =
                            new InputStreamReader(
                                    Preconditions.checkNotNull(
                                            Thread.currentThread().getContextClassLoader().getResourceAsStream(path),
                                            "Resource from classpath(" + propertiesURI + ") is NULL"
                                    ),
                                    StandardCharsets.UTF_8
                            )
            ) {
                properties.load(reader);
            }
        } else if ("file".equalsIgnoreCase(scheme)) {
            if (log.isDebugEnabled()) {
                log.debug("Loading from filesystem.({})", uri);
            }
            try (
                    BufferedReader reader =
                            Files.newBufferedReader(
                                    Preconditions.checkNotNull(
                                            Paths.get(uri),
                                            "Resource from filesystem(" + propertiesURI + ") is NULL"
                                    ),
                                    StandardCharsets.UTF_8
                            )
            ) {
                properties.load(reader);
            }
        }

        return properties;
    }
}
