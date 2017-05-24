/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.uma.ws.rs;

import com.wordnik.swagger.annotations.*;
import org.slf4j.Logger;
import org.xdi.oxauth.model.configuration.AppConfiguration;
import org.xdi.oxauth.model.error.ErrorResponseFactory;
import org.xdi.oxauth.model.uma.PermissionTicket;
import org.xdi.oxauth.model.uma.UmaConstants;
import org.xdi.oxauth.model.uma.UmaErrorResponseType;
import org.xdi.oxauth.model.uma.persistence.UmaPermission;
import org.xdi.oxauth.service.token.TokenService;
import org.xdi.oxauth.service.uma.UmaPermissionManager;
import org.xdi.oxauth.service.uma.UmaValidationService;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.Calendar;
import java.util.Date;

/**
 * The endpoint at which the host registers permissions that it anticipates a
 * requester will shortly be asking for from the AM. This AM's endpoint is part
 * of resource set registration API.
 * <p/>
 * In response to receiving an access request accompanied by an RPT that is
 * invalid or has insufficient authorization data, the host SHOULD register a
 * permission with the AM that would be sufficient for the type of access
 * sought. The AM returns a permission ticket for the host to give to the
 * requester in its response.
 *
 * @author Yuriy Zabrovarnyy
 */
@Path("/host/rsrc_pr")
@Api(value = "/host/rsrc_pr", description = "The resource server uses the protection API's permission registration endpoint to register a requested permission with the authorization server that would suffice for the client's access attempt. The authorization server returns a permission ticket for the resource server to give to the client in its response. The PAT provided in the API request implicitly identifies the resource owner (\"subject\") to which the permission applies.\n" +
        "\n" +
        "Note: The resource server is free to choose the extent of the requested permission that it registers, as long as it minimally suffices for the access attempted by the client. For example, it can choose to register a permission that covers several scopes or a resource set that is greater in extent than the specific resource that the client attempted to access. Likewise, the authorization server is ultimately free to choose to partially fulfill the elements of a permission request based on incomplete satisfaction of policy criteria, or not to fulfill the request.\n" +
        "\n" +
        "The resource server uses the POST method at the endpoint. The body of the HTTP request message contains a JSON object providing the requested permission, using a format derived from the scope description format specified in [OAuth-resource-reg], as follows. The object has the following properties:")
public class UmaPermissionRegistrationWS {

    public static final int DEFAULT_PERMISSION_LIFETIME = 3600;

    @Inject
    private Logger log;

    @Inject
    private TokenService tokenService;

    @Inject
    private UmaPermissionManager permissionManager;

    @Inject
    private ErrorResponseFactory errorResponseFactory;

    @Inject
    private UmaValidationService umaValidationService;

    @Inject
	private AppConfiguration appConfiguration;

    @POST
    @Consumes({UmaConstants.JSON_MEDIA_TYPE})
    @Produces({UmaConstants.JSON_MEDIA_TYPE})
    @ApiOperation(value = "Registers permission using the POST method",
            consumes = UmaConstants.JSON_MEDIA_TYPE,
            produces = UmaConstants.JSON_MEDIA_TYPE,
            notes = "The resource server uses the POST method at the endpoint. The body of the HTTP request message contains a JSON object providing the requested permission, using a format derived from the scope description format specified in [OAuth-resource-reg], as follows. The object has the following properties:")
    @ApiResponses(value = {
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 400, message = "Bad Request")
    })
    public Response registerPermission(@Context HttpServletRequest request,
                                       @HeaderParam("Authorization") String authorization,
                                       @HeaderParam("Host") String amHost,
                                       @ApiParam(value = "The identifier for a resource to which this client is seeking access. The identifier MUST correspond to a resource set that was previously registered.", required = true)
                                           org.xdi.oxauth.model.uma.UmaPermission permissionRequest) {
        try {
            umaValidationService.assertHasProtectionScope(authorization);
            String validatedAmHost = umaValidationService.validateAmHost(amHost);
            umaValidationService.validateResource(permissionRequest);

            final UmaPermission permission = permissionManager.createPermission(validatedAmHost, permissionRequest, rptExpirationDate());
            permissionManager.addPermission(permission, tokenService.getClientDn(authorization));

            return Response.status(Response.Status.CREATED).
                            entity(new PermissionTicket(permission.getTicket())).
                            build();
        } catch (Exception ex) {
            if (ex instanceof WebApplicationException) {
                throw (WebApplicationException) ex;
            }

            log.error("Exception happened", ex);
            throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errorResponseFactory.getUmaJsonErrorResponse(UmaErrorResponseType.SERVER_ERROR)).build());
        }
    }

    public Date rptExpirationDate() {
        int lifeTime = appConfiguration.getUmaRequesterPermissionTokenLifetime();
        if (lifeTime <= 0) {
            lifeTime = DEFAULT_PERMISSION_LIFETIME;
        }

        final Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND, lifeTime);
        return calendar.getTime();
    }
}
