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
import io.restassured.config.RedirectConfig;
import io.restassured.config.RestAssuredConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.it.junit5.extension.CLIResult;
import org.keycloak.it.junit5.extension.DistributionTest;

import static io.restassured.RestAssured.when;

@DistributionTest(keepAlive = true,
        enableTls = true,
        defaultOptions = {"--health-enabled=true", "--metrics-enabled=true", "--management-enabled=true"},
        containerExposedPorts = {8080, 9000})
public class ManagementHttpsDistTest {

    @BeforeAll
    public static void setRestAssuredHttps() {
        RestAssured.useRelaxedHTTPSValidation();
        RestAssuredConfig config = RestAssured.config;
        RestAssured.config = config.redirect(RedirectConfig.redirectConfig().followRedirects(false));
    }

    @BeforeEach
    public void setUp() {
        RestAssured.port = 9000;
    }

    @AfterEach
    public void tearDown() {
        RestAssured.reset();
    }

    @Test
    @Launch({"start-dev", "--management-https-enabled=true"})
    public void simpleHttpsStartDev(LaunchResult result) {
        assertHttpsWorks(result);
    }

    @Test
    @Launch({"start-dev"})
    public void httpsShouldWorkWhenHttpsEnabledOnServer(LaunchResult result) {
        assertHttpsWorks(result);
    }

    @Test
    @Launch({"start", "--hostname-strict=false"})
    public void simpleHttpsStart(LaunchResult result) {
        assertHttpsWorks(result);
    }

    @Test
    @Launch({"start-dev", "--management-https-enabled=false"})
    public void httpsDisabled(LaunchResult result) {
        assertHttpWorks(result);
    }

    private void assertHttpWorks(LaunchResult result) {
        checkHttp(result, false);
    }

    private void assertHttpsWorks(LaunchResult result) {
        checkHttp(result, true);
    }

    private void checkHttp(LaunchResult result, boolean isHttps) {
        CLIResult cliResult = (CLIResult) result;
        var schema = isHttps ? "https" : "http";
        var url = schema + "://localhost:9000";
        cliResult.assertMessage("Management interface listening on " + schema + "://0.0.0.0:9000");

        when().get(url + "/health").then()
                .statusCode(200);
        when().get(url + "/health/live").then()
                .statusCode(200);
        when().get(url + "/health/ready").then()
                .statusCode(200);
        when().get(url + "/metrics").then()
                .statusCode(200);
    }
}
