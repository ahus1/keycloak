/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.quarkus.runtime.configuration.mappers;

import io.smallrye.config.ConfigSourceInterceptorContext;
import org.keycloak.config.HttpOptions;
import org.keycloak.config.ManagementOptions;
import org.keycloak.quarkus.runtime.configuration.Configuration;

import java.util.Optional;

import static org.keycloak.quarkus.runtime.configuration.Configuration.isTrue;
import static org.keycloak.quarkus.runtime.configuration.mappers.PropertyMapper.fromOption;

public class ManagementPropertyMappers {
    private static final String MANAGEMENT_ENABLED_MSG = "Management interface is enabled";
    private static final String MANAGEMENT_HTTPS_ENABLED_MSG = "Management interface and HTTPS is enabled";

    private ManagementPropertyMappers() {
    }

    public static PropertyMapper<?>[] getManagementPropertyMappers() {
        return new PropertyMapper[]{
                fromOption(ManagementOptions.MANAGEMENT_ENABLED)
                        .to("quarkus.management.enabled")
                        .paramLabel(Boolean.TRUE + "|" + Boolean.FALSE)
                        .build(),
                fromOption(ManagementOptions.MANAGEMENT_RELATIVE_PATH)
                        .isEnabled(ManagementPropertyMappers::isManagementEnabled, MANAGEMENT_ENABLED_MSG)
                        .to("quarkus.management.root-path")
                        .paramLabel("path")
                        .build(),
                fromOption(ManagementOptions.MANAGEMENT_PORT)
                        .isEnabled(ManagementPropertyMappers::isManagementEnabled, MANAGEMENT_ENABLED_MSG)
                        .to("quarkus.management.port")
                        .paramLabel("port")
                        .build(),
                fromOption(ManagementOptions.MANAGEMENT_HOST)
                        .isEnabled(ManagementPropertyMappers::isManagementEnabled, MANAGEMENT_ENABLED_MSG)
                        .mapFrom(HttpOptions.HTTP_HOST.getKey())
                        .to("quarkus.management.host")
                        .paramLabel("host")
                        .build(),
                // HTTPS
                fromOption(ManagementOptions.MANAGEMENT_HTTPS_ENABLED)
                        .isEnabled(ManagementPropertyMappers::isManagementEnabled, MANAGEMENT_ENABLED_MSG)
                        .paramLabel(Boolean.TRUE + "|" + Boolean.FALSE)
                        .transformer(ManagementPropertyMappers::getHttpsEnabledTransformer)
                        .build(),
                fromOption(ManagementOptions.MANAGEMENT_HTTPS_CLIENT_AUTH)
                        .isEnabled(ManagementPropertyMappers::isHttpsEnabled, MANAGEMENT_HTTPS_ENABLED_MSG)
                        .mapFrom(HttpOptions.HTTPS_CLIENT_AUTH.getKey())
                        .to("quarkus.management.ssl.client-auth")
                        .paramLabel("auth")
                        .build(),
                fromOption(ManagementOptions.MANAGEMENT_HTTPS_CIPHER_SUITES)
                        .isEnabled(ManagementPropertyMappers::isHttpsEnabled, MANAGEMENT_HTTPS_ENABLED_MSG)
                        .mapFrom(HttpOptions.HTTPS_CIPHER_SUITES.getKey())
                        .to("quarkus.management.ssl.cipher-suites")
                        .paramLabel("ciphers")
                        .build(),
                fromOption(ManagementOptions.MANAGEMENT_HTTPS_PROTOCOLS)
                        .isEnabled(ManagementPropertyMappers::isHttpsEnabled, MANAGEMENT_HTTPS_ENABLED_MSG)
                        .mapFrom(HttpOptions.HTTPS_PROTOCOLS.getKey())
                        .to("quarkus.management.ssl.protocols")
                        .paramLabel("protocols")
                        .build(),
                fromOption(ManagementOptions.MANAGEMENT_HTTPS_CERTIFICATE_FILE)
                        .isEnabled(ManagementPropertyMappers::isHttpsEnabled, MANAGEMENT_HTTPS_ENABLED_MSG)
                        .mapFrom(HttpOptions.HTTPS_CERTIFICATE_FILE.getKey())
                        .to("quarkus.management.ssl.certificate.files")
                        .paramLabel("file")
                        .build(),
                fromOption(ManagementOptions.MANAGEMENT_HTTPS_CERTIFICATE_KEY_FILE)
                        .isEnabled(ManagementPropertyMappers::isHttpsEnabled, MANAGEMENT_HTTPS_ENABLED_MSG)
                        .mapFrom(HttpOptions.HTTPS_CERTIFICATE_KEY_FILE.getKey())
                        .to("quarkus.management.ssl.certificate.key-files")
                        .paramLabel("file")
                        .build(),
                fromOption(ManagementOptions.MANAGEMENT_HTTPS_KEY_STORE_FILE)
                        .isEnabled(ManagementPropertyMappers::isHttpsEnabled, MANAGEMENT_HTTPS_ENABLED_MSG)
                        .mapFrom(HttpOptions.HTTPS_KEY_STORE_FILE.getKey())
                        .to("quarkus.management.ssl.certificate.key-store-file")
                        .paramLabel("file")
                        .build(),
                fromOption(ManagementOptions.MANAGEMENT_HTTPS_KEY_STORE_PASSWORD)
                        .isEnabled(ManagementPropertyMappers::isHttpsEnabled, MANAGEMENT_HTTPS_ENABLED_MSG)
                        .mapFrom(HttpOptions.HTTPS_KEY_STORE_PASSWORD.getKey())
                        .to("quarkus.management.ssl.certificate.key-store-password")
                        .paramLabel("password")
                        .isMasked(true)
                        .build(),
                fromOption(ManagementOptions.MANAGEMENT_HTTPS_KEY_STORE_TYPE)
                        .isEnabled(ManagementPropertyMappers::isHttpsEnabled, MANAGEMENT_HTTPS_ENABLED_MSG)
                        .mapFrom(HttpOptions.HTTPS_KEY_STORE_TYPE.getKey())
                        .to("quarkus.management.ssl.certificate.key-store-file-type")
                        .paramLabel("type")
                        .build(),
        };
    }

    public static boolean isManagementEnabled() {
        return isTrue(ManagementOptions.MANAGEMENT_ENABLED);
    }

    public static boolean isHttpsEnabled() {
        return isManagementEnabled() && isTrue(ManagementOptions.MANAGEMENT_HTTPS_ENABLED);
    }

    private static Optional<String> getHttpsEnabledTransformer(Optional<String> value, ConfigSourceInterceptorContext context) {
        if (value.isPresent()) return Optional.of(Boolean.valueOf(value.get()).toString());

        var keyStore = Configuration.getOptionalValue("quarkus.http.ssl.certificate.key-store");
        var keyStoreFile = Configuration.getOptionalValue("quarkus.http.ssl.certificate.key-store-file");

        var isHttpsEnabled = keyStore.isPresent() && keyStoreFile.isPresent() ? Boolean.TRUE : Boolean.FALSE;
        return Optional.of(isHttpsEnabled).map(Object::toString);
    }
}
