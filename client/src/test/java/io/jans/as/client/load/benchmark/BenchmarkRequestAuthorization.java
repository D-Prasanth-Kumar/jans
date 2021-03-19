/*
 * Janssen Project software is available under the Apache License (2004). See http://www.apache.org/licenses/ for full text.
 *
 * Copyright (c) 2020, Janssen Project
 */

package io.jans.as.client.load.benchmark;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.testng.Reporter;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Listeners;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import io.jans.as.client.AuthorizationRequest;
import io.jans.as.client.AuthorizationResponse;
import io.jans.as.client.BaseTest;
import io.jans.as.client.RegisterClient;
import io.jans.as.client.RegisterRequest;
import io.jans.as.client.RegisterResponse;
import io.jans.as.client.load.benchmark.suite.BenchmarkTestListener;
import io.jans.as.client.load.benchmark.suite.BenchmarkTestSuiteListener;
import io.jans.as.model.common.ResponseType;
import io.jans.as.model.common.SubjectType;
import io.jans.as.model.register.ApplicationType;
import io.jans.as.model.util.StringUtils;

/**
 * @author Yuriy Movchan
 * @author Javier Rojas Blum
 * @version November 29, 2017
 */

@Listeners({BenchmarkTestSuiteListener.class, BenchmarkTestListener.class})
public class BenchmarkRequestAuthorization extends BaseTest {

    private String clientId;
    private String clientSecret;

    @Parameters({"userId", "userSecret", "redirectUris", "sectorIdentifierUri"})
    @BeforeClass
    public void registerClient(final String userId, final String userSecret, String redirectUris, String sectorIdentifierUri) throws Exception {
        Reporter.log("Register client", true);

        List<ResponseType> responseTypes = Arrays.asList(ResponseType.CODE, ResponseType.ID_TOKEN);
        List<String> scopes = Arrays.asList("openid", "profile", "address", "email", "user_name");

        RegisterResponse registerResponse = registerClient(redirectUris, responseTypes, scopes, sectorIdentifierUri);

        assertEquals(registerResponse.getStatus(), 200, "Unexpected response code: " + registerResponse.getEntity());
        assertNotNull(registerResponse.getClientId());
        assertNotNull(registerResponse.getClientSecret());
        assertNotNull(registerResponse.getRegistrationAccessToken());
        assertNotNull(registerResponse.getClientIdIssuedAt());
        assertNotNull(registerResponse.getClientSecretExpiresAt());

        this.clientId = registerResponse.getClientId();
        this.clientSecret = registerResponse.getClientSecret();
    }

    @Parameters({"userId", "userSecret", "redirectUri"})
    @Test(invocationCount = 200, threadPoolSize = 1)
    public void testAuthorization1(final String userId, final String userSecret, final String redirectUri) throws Exception {
        testAuthorizationImpl(userId, userSecret, this.clientId, redirectUri, false);
    }

    @Parameters({"userId", "userSecret", "redirectUri"})
    @Test(invocationCount = 200, threadPoolSize = 5, dependsOnMethods = {"testAuthorization1"})
    public void testAuthorization2(final String userId, final String userSecret, final String redirectUri) throws Exception {
        testAuthorizationImpl(userId, userSecret, this.clientId, redirectUri, true);
    }

    @Parameters({"userId", "userSecret", "redirectUri"})
    @Test(invocationCount = 200, threadPoolSize = 2, dependsOnMethods = {"testAuthorization2"})
    public void testAuthorization3(final String userId, final String userSecret, final String redirectUri) throws Exception {
        testAuthorizationImpl(userId, userSecret, this.clientId, redirectUri, true);
    }

    private void testAuthorizationImpl(final String userId, final String userSecret, final String clientId, final String redirectUri, boolean useNewDriver) {
        List<ResponseType> responseTypes = Arrays.asList(ResponseType.CODE, ResponseType.ID_TOKEN);
        List<String> scopes = Arrays.asList("openid", "profile", "address", "email", "user_name");
        String nonce = UUID.randomUUID().toString();

        AuthorizationResponse response = requestAuthorization(userId, userSecret, redirectUri, responseTypes, scopes, clientId, nonce, useNewDriver);

        assertNotNull(response.getLocation(), "The location is null");
        assertNotNull(response.getCode(), "The authorization code is null");
        assertNotNull(response.getState(), "The state is null");
        assertNotNull(response.getScope(), "The scope is null");
    }

    private AuthorizationResponse requestAuthorization(final String userId, final String userSecret, final String redirectUri,
                                                       List<ResponseType> responseTypes, List<String> scopes, String clientId, String nonce, boolean useNewDriver) {
        String state = UUID.randomUUID().toString();

        AuthorizationRequest authorizationRequest = new AuthorizationRequest(responseTypes, clientId, scopes, redirectUri, nonce);
        authorizationRequest.setState(state);

        AuthorizationResponse authorizationResponse = authenticateResourceOwnerAndGrantAccess(
                authorizationEndpoint, authorizationRequest, userId, userSecret, true, useNewDriver);

        return authorizationResponse;
    }

    private RegisterResponse registerClient(
            final String redirectUris, List<ResponseType> responseTypes, List<String> scopes, String sectorIdentifierUri) {
        RegisterRequest registerRequest = new RegisterRequest(ApplicationType.WEB, "Jans Auth benchmark test app",
                StringUtils.spaceSeparatedToList(redirectUris));
        registerRequest.setResponseTypes(responseTypes);
        registerRequest.setScope(scopes);
        registerRequest.setSubjectType(SubjectType.PAIRWISE);
        registerRequest.setSectorIdentifierUri(sectorIdentifierUri);

        RegisterClient registerClient = new RegisterClient(registrationEndpoint);
        registerClient.setRequest(registerRequest);
        RegisterResponse registerResponse = registerClient.exec();

        showClient(registerClient);
        assertEquals(registerResponse.getStatus(), 200, "Unexpected response code: " + registerResponse.getEntity());
        assertNotNull(registerResponse.getClientId());
        assertNotNull(registerResponse.getClientSecret());
        assertNotNull(registerResponse.getRegistrationAccessToken());
        assertNotNull(registerResponse.getClientIdIssuedAt());
        assertNotNull(registerResponse.getClientSecretExpiresAt());

        return registerResponse;
    }

}
