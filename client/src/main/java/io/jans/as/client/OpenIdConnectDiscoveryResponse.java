/*
 * Janssen Project software is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2020, Janssen Project
 */

package io.jans.as.client;

import java.util.ArrayList;
import java.util.List;

import io.jans.as.model.discovery.WebFingerLink;

/**
 * @author Javier Rojas Blum Date: 01.28.2013
 */
public class OpenIdConnectDiscoveryResponse extends BaseResponse {

    private String subject;
    private List<WebFingerLink> links;

    /**
     * Constructs an OpenID Connect Discovery Response.
     *
     * @param status The response status code.
     */
    public OpenIdConnectDiscoveryResponse(int status) {
        super(status);
        links = new ArrayList<WebFingerLink>();
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public List<WebFingerLink> getLinks() {
        return links;
    }

    public void setLinks(List<WebFingerLink> links) {
        this.links = links;
    }
}