/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2020, Gluu
 */

package org.gluu.oxauth.fido2.ws.rs.controller;

import java.io.IOException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.gluu.oxauth.fido2.exception.Fido2RPRuntimeException;
import org.gluu.oxauth.fido2.service.DataMapperService;
import org.gluu.oxauth.fido2.service.operation.AssertionService;
import org.gluu.oxauth.model.configuration.AppConfiguration;

import com.fasterxml.jackson.databind.JsonNode;

@ApplicationScoped
@Path("/fido2/assertion")
public class AssertionController {

    @Inject
    private AssertionService assertionService;

    @Inject
    private DataMapperService dataMapperService;

    @Inject
    private AppConfiguration appConfiguration;

    @POST
    @Consumes({ "application/json" })
    @Produces({ "application/json" })
    @Path("/options")
    public Response authenticate(String content) {
        if ((appConfiguration.getFido2Configuration() == null) || appConfiguration.getFido2Configuration().isDisable()) {
            return Response.status(Status.FORBIDDEN).build();
        }

        JsonNode params;
        try {
        	params = dataMapperService.readTree(content);
        } catch (IOException ex) {
            throw new Fido2RPRuntimeException("Failed to parse options assertion request", ex);
        }

        JsonNode result = assertionService.options(params);

        ResponseBuilder builder = Response.ok().entity(result.toString());
        return builder.build();
    }

    @POST
    @Consumes({ "application/json" })
    @Produces({ "application/json" })
    @Path("/result")
    public Response verify(String content) {
        if ((appConfiguration.getFido2Configuration() == null) || appConfiguration.getFido2Configuration().isDisable()) {
            return Response.status(Status.FORBIDDEN).build();
        }

        JsonNode params;
        try {
        	params = dataMapperService.readTree(content);
        } catch (IOException ex) {
            throw new Fido2RPRuntimeException("Failed to parse finish assertion request", ex);
        }

        JsonNode result = assertionService.verify(params);

        ResponseBuilder builder = Response.ok().entity(result.toString());
        return builder.build();
    }

}
