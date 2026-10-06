package org.keycloak.encoding;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.keycloak.Config;
import org.keycloak.common.Version;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.services.resources.KeycloakApplication;

import org.apache.commons.io.FileUtils;
import org.jboss.logging.Logger;

public class GzipResourceEncodingProviderFactory implements ResourceEncodingProviderFactory {

    private static final Logger logger = Logger.getLogger(GzipResourceEncodingProviderFactory.class);

    private Set<String> excludedContentTypes = new HashSet<>();

    private volatile File cacheDir;
    private volatile File previousCacheDir;

    @Override
    public ResourceEncodingProvider create(KeycloakSession session) {
        if (cacheDir == null) {
            cacheDir = initCacheDir();
        }

        return new GzipResourceEncodingProvider(cacheDir);
    }

    @Override
    public void init(Config.Scope config) {
        String e = config.get("excludedContentTypes", "image/png image/jpeg");
        excludedContentTypes.addAll(Arrays.asList(e.split(" ")));
    }

    @Override
    public boolean encodeContentType(String contentType) {
        return !excludedContentTypes.contains(contentType);
    }

    @Override
    public String getId() {
        return "gzip";
    }

    @Override
    public void clearCache() {
        File prev = previousCacheDir;
        if (prev != null) {
            try {
                FileUtils.deleteDirectory(prev);
            } catch (IOException e) {
                logger.warn("Failed to delete previous gzip cache directory", e);
            }
        }

        // current dir becomes previous — in-flight providers may still write to it
        previousCacheDir = cacheDir;

        File cacheRoot = new File(KeycloakApplication.getTmpDirectory(), "kc-gzip-cache");
        File newDir = new File(cacheRoot, Version.RESOURCES_VERSION + "-" + System.nanoTime());
        newDir.mkdirs();
        if (newDir.isDirectory()) {
            cacheDir = newDir;
        } else {
            logger.warn("Failed to create gzip cache directory " + newDir.getAbsolutePath());
            cacheDir = null;
        }
    }

    @Override
    public List<ProviderConfigProperty> getConfigMetadata() {
        return ProviderConfigurationBuilder.create()
                .property()
                .name("excludedContentTypes")
                .type("string")
                .helpText("A space separated list of content-types to exclude from encoding.")
                .defaultValue("image/png image/jpeg")
                .add()
                .build();
    }

    private synchronized File initCacheDir() {
        if (cacheDir != null) {
            return cacheDir;
        }

        File cacheRoot = new File(KeycloakApplication.getTmpDirectory(), "kc-gzip-cache");

        // clean up all directories from previous runs or clearCache() generations (#52802)
        if (cacheRoot.isDirectory()) {
            File[] files = cacheRoot.listFiles();
            if (files != null) {
                for (File f : files) {
                    try {
                        FileUtils.deleteDirectory(f);
                    } catch (IOException e) {
                        logger.warn("Failed to delete old gzip cache directory", e);
                    }
                }
            }
        }

        File cacheDir = new File(cacheRoot, Version.RESOURCES_VERSION);
        cacheDir.mkdirs();
        if (!cacheDir.isDirectory()) {
            logger.warn("Failed to create gzip cache directory " + cacheDir.getAbsolutePath());
            return null;
        }

        return cacheDir;
    }
}
