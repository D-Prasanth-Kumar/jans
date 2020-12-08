/*
 * Janssen Project software is available under the Apache License (2004). See http://www.apache.org/licenses/ for full text.
 *
 * Copyright (c) 2020, Janssen Project
 */

package io.jans.as.rp.action;

import io.jans.as.client.EndSessionRequest;
import org.slf4j.Logger;

import javax.enterprise.context.SessionScoped;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;

/**
 * @author Javier Rojas Blum
 * @version February 23, 2016
 */
@Named
@SessionScoped
public class EndSessionAction implements Serializable {

	private static final long serialVersionUID = 6785573643861198737L;

	@Inject
    private Logger log;

    private String endSessionEndpoint;
    private String idTokenHint;
    private String postLogoutRedirectUri;
    private String state;

    private boolean showResults;
    private String requestString;
    private String responseString;

    public void exec() {
        try {
            EndSessionRequest req = new EndSessionRequest(idTokenHint, postLogoutRedirectUri, state);

            String authorizationRequest = endSessionEndpoint + "?" + req.getQueryString();
            FacesContext.getCurrentInstance().getExternalContext().redirect(authorizationRequest);
        } catch (Exception e) {
        	log.error(e.getMessage(), e);
        }
    }

    public String getEndSessionEndpoint() {
        return endSessionEndpoint;
    }

    public void setEndSessionEndpoint(String endSessionEndpoint) {
        this.endSessionEndpoint = endSessionEndpoint;
    }

    public String getIdTokenHint() {
        return idTokenHint;
    }

    public void setIdTokenHint(String idTokenHint) {
        this.idTokenHint = idTokenHint;
    }

    public String getPostLogoutRedirectUri() {
        return postLogoutRedirectUri;
    }

    public void setPostLogoutRedirectUri(String postLogoutRedirectUri) {
        this.postLogoutRedirectUri = postLogoutRedirectUri;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
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
}