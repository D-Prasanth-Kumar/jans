/*
 * Janssen Project software is available under the Apache License (2004). See http://www.apache.org/licenses/ for full text.
 *
 * Copyright (c) 2020, Janssen Project
 */

package io.jans.as.rp.action;

import java.io.Serializable;

import javax.enterprise.context.SessionScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.Logger;

import io.jans.as.client.UserInfoClient;
import io.jans.as.client.UserInfoRequest;
import io.jans.as.model.common.AuthorizationMethod;

/**
 * @author Javier Rojas Blum Date: 02.22.2012
 */
@Named
@SessionScoped
public class UserInfoAction implements Serializable {

	private static final long serialVersionUID = 8127882852081260414L;

	@Inject
    private Logger log;

    private String userInfoEndpoint;
    private String accessToken;

    private boolean showResults;
    private String requestString;
    private String responseString;

    private AuthorizationMethod authorizationMethod;

    public void exec() {
        try {
            UserInfoRequest request = new UserInfoRequest(accessToken);
            request.setAuthorizationMethod(authorizationMethod);

            UserInfoClient client = new UserInfoClient(userInfoEndpoint);
            client.setRequest(request);
            client.exec();

            showResults = true;
            requestString = client.getRequestAsString();
            responseString = client.getResponseAsString();
        } catch (Exception e) {
        	log.error(e.getMessage(), e);
        }
    }

    @Produces
    @Named("authorizationMethods")
    public AuthorizationMethod[] authorizationMethods() {
        return AuthorizationMethod.values();
    }

    public String getUserInfoEndpoint() {
        return userInfoEndpoint;
    }

    public void setUserInfoEndpoint(String userInfoEndpoint) {
        this.userInfoEndpoint = userInfoEndpoint;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public boolean isShowResults() {
        return showResults;
    }

    public void setShowResults(boolean showResults) {
        this.showResults = showResults;
    }

    public String getRequestString() {
        return requestString;
    }

    public void setRequestString(String requestString) {
        this.requestString = requestString;
    }

    public String getResponseString() {
        return responseString;
    }

    public void setResponseString(String responseString) {
        this.responseString = responseString;
    }

    public AuthorizationMethod getAuthorizationMethod() {
        return authorizationMethod;
    }

    public void setAuthorizationMethod(AuthorizationMethod authorizationMethod) {
        this.authorizationMethod = authorizationMethod;
    }
}