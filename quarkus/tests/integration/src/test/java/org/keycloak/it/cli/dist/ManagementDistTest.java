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
package org.keycloak.it.cli.dist;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.it.junit5.extension.CLIResult;
import org.keycloak.it.junit5.extension.DistributionTest;

import java.net.ConnectException;

import static io.restassured.RestAssured.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DistributionTest(keepAlive = true, defaultOptions = {"--health-enabled=true", "--metrics-enabled=true"}, containerExposedPorts = {8080, 9000, 9005})
public class ManagementDistTest {

    @BeforeEach
    public void setUp() {
        RestAssured.port = 9000;
    }

    @AfterEach
    public void tearDown() {
        RestAssured.reset();
    }

    @Test
    @Launch({"start-dev", "--management-enabled=false"})
    void testManagementDisabled(LaunchResult result) {
        CLIResult cliResult = (CLIResult) result;
        cliResult.assertNoMessage("Management interface listening on");

        assertThrows(ConnectException.class, () -> when().get("/"), "Connection refused must be thrown");
        assertThrows(ConnectException.class, () -> when().get("/health"), "Connection refused must be thrown");

        RestAssured.reset();

        when().get("/health").then()
                .statusCode(200);
    }

    @Test
    @Launch({"start-dev", "--management-enabled=true"})
    void testManagementEnabled(LaunchResult result) {
        CLIResult cliResult = (CLIResult) result;
        cliResult.assertMessage("Management interface listening on http://0.0.0.0:9000");

        when().get("/").then()
                .statusCode(404);
        when().get("/health").then()
                .statusCode(200);
        when().get("/health/live").then()
                .statusCode(200);
        when().get("/health/ready").then()
                .statusCode(200);
        when().get("/metrics").then()
                .statusCode(200);
    }

    @Test
    @Launch({"start-dev", "--management-enabled=true", "--management-port=9005"})
    void testManagementDifferentPort(LaunchResult result) {
        CLIResult cliResult = (CLIResult) result;
        cliResult.assertMessage("Management interface listening on http://0.0.0.0:9005");

        RestAssured.port = 9005;

        when().get("/").then()
                .statusCode(404);
        when().get("/health").then()
                .statusCode(200);
        when().get("/health/live").then()
                .statusCode(200);
        when().get("/health/ready").then()
                .statusCode(200);
        when().get("/metrics").then()
                .statusCode(200);
    }

    @Test
    @Launch({"start-dev", "--management-enabled=true", "--management-relative-path=/management"})
    void testManagementDifferentRelativePath(LaunchResult result) {
        CLIResult cliResult = (CLIResult) result;
        cliResult.assertMessage("Management interface listening on http://0.0.0.0:9000");

        when().get("/management").then()
                .statusCode(404);
        when().get("/management/health").then()
                .statusCode(200);
        when().get("/health").then()
                .statusCode(404);
        when().get("/management/health/live").then()
                .statusCode(200);
        when().get("/management/health/ready").then()
                .statusCode(200);
        when().get("/management/metrics").then()
                .statusCode(200);
        when().get("/metrics").then()
                .statusCode(404);
    }

    @Test
    @Launch({"start-dev", "--management-enabled=true", "--management-host=127.0.0.1"})
    void testManagementDifferentHost(LaunchResult result) {
        CLIResult cliResult = (CLIResult) result;
        cliResult.assertMessage("Management interface listening on http://127.0.0.1:9000");

        RestAssured.baseURI = "http://127.0.0.1";

        when().get("/").then()
                .statusCode(404);
        when().get("/health").then()
                .statusCode(200);
        when().get("/health/live").then()
                .statusCode(200);
        when().get("/health/ready").then()
                .statusCode(200);
        when().get("/metrics").then()
                .statusCode(200);
    }
}
