/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.gluu.oxauth.authorize.ws.rs;

import org.gluu.oxauth.audit.ApplicationAuditLogger;
import org.gluu.oxauth.ciba.CIBAPingCallbackService;
import org.gluu.oxauth.ciba.CIBAPushTokenDeliveryService;
import org.gluu.oxauth.model.audit.Action;
import org.gluu.oxauth.model.audit.OAuth2AuditLog;
import org.gluu.oxauth.model.authorize.DeviceAuthorizationResponseParam;
import org.gluu.oxauth.model.authorize.ScopeChecker;
import org.gluu.oxauth.model.common.AuthorizationGrantList;
import org.gluu.oxauth.model.common.DeviceAuthorizationCacheControl;
import org.gluu.oxauth.model.common.DeviceFlowRequestStatus;
import org.gluu.oxauth.model.config.ConfigurationFactory;
import org.gluu.oxauth.model.configuration.AppConfiguration;
import org.gluu.oxauth.model.crypto.AbstractCryptoProvider;
import org.gluu.oxauth.model.error.ErrorResponseFactory;
import org.gluu.oxauth.model.registration.Client;
import org.gluu.oxauth.model.session.SessionClient;
import org.gluu.oxauth.model.util.StringUtils;
import org.gluu.oxauth.security.Identity;
import org.gluu.oxauth.service.*;
import org.gluu.oxauth.service.ciba.CibaRequestService;
import org.gluu.oxauth.service.external.ExternalPostAuthnService;
import org.gluu.oxauth.util.ServerUtil;
import org.gluu.util.StringHelper;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.gluu.oxauth.model.ciba.BackchannelAuthenticationErrorResponseType.INVALID_CLIENT;

/**
 * Implementation for device authorization rest service.
 */
@Path("/")
public class DeviceAuthorizationRestWebServiceImpl implements DeviceAuthorizationRestWebService {

    @Inject
    private Logger log;

    @Inject
    private ApplicationAuditLogger applicationAuditLogger;

    @Inject
    private ErrorResponseFactory errorResponseFactory;

    @Inject
    private AuthorizationGrantList authorizationGrantList;

    @Inject
    private ClientService clientService;

    @Inject
    private UserService userService;

    @Inject
    private Identity identity;

    @Inject
    private AuthenticationFilterService authenticationFilterService;

    @Inject
    private SessionIdService sessionIdService;

    @Inject CookieService cookieService;

    @Inject
    private ScopeChecker scopeChecker;

    @Inject
    private ClientAuthorizationsService clientAuthorizationsService;

    @Inject
    private RequestParameterService requestParameterService;

    @Inject
    private AppConfiguration appConfiguration;

    @Inject
    private ConfigurationFactory сonfigurationFactory;

    @Inject
    private AbstractCryptoProvider cryptoProvider;

    @Inject
    private AuthorizeRestWebServiceValidator authorizeRestWebServiceValidator;

    @Inject
    private CIBAPushTokenDeliveryService cibaPushTokenDeliveryService;

    @Inject
    private CIBAPingCallbackService cibaPingCallbackService;

    @Inject
    private ExternalPostAuthnService externalPostAuthnService;

    @Inject
    private CibaRequestService cibaRequestService;

    @Context
    private HttpServletRequest servletRequest;


    @Override
    public Response deviceAuthorization(String clientId, String scope, HttpServletRequest httpRequest,
                                        HttpServletResponse httpResponse, SecurityContext securityContext) {
        scope = ServerUtil.urlDecode(scope); // it may be encoded

        OAuth2AuditLog oAuth2AuditLog = new OAuth2AuditLog(ServerUtil.getIpAddress(httpRequest), Action.BACKCHANNEL_AUTHENTICATION);
        oAuth2AuditLog.setClientId(clientId);
        oAuth2AuditLog.setScope(scope);

        log.debug("Attempting to request device codes: clientId = {}, scope = {}", clientId, scope);

        SessionClient sessionClient = identity.getSessionClient();
        Client client = null;
        if (sessionClient != null) {
            client = sessionClient.getClient();
        }
        if (client == null) {
            throw errorResponseFactory.createWebApplicationException(Response.Status.UNAUTHORIZED, INVALID_CLIENT, "");
        }

        List<String> scopes = new ArrayList<>();
        if (StringHelper.isNotEmpty(scope)) {
            Set<String> grantedScopes = scopeChecker.checkScopesPolicy(client, scope);
            scopes.addAll(grantedScopes);
        }

        String userCode = StringUtils.generateRandomReadableCode((byte) 8); // Entropy 20^8 which is suggested in the RFC8628 section 6.1
        String deviceCode = StringUtils.generateRandomCode((byte) 24); // Entropy 160 bits which is over userCode entropy based on RFC8628 section 5.2
        URI verificationUri = URI.create(appConfiguration.getIssuer()).resolve("device-code");
        URI verificationUriComplete = UriBuilder.fromUri(verificationUri).queryParam(DeviceAuthorizationResponseParam.USER_CODE, userCode).build();
        int expiresIn = appConfiguration.getDeviceAuthorizationRequestExpiresIn();
        int interval = appConfiguration.getDeviceAuthorizationTokenPoolInterval();
        long lastAccess = System.currentTimeMillis();
        DeviceFlowRequestStatus status = DeviceFlowRequestStatus.PENDING;

        DeviceAuthorizationCacheControl deviceAuthorizationCacheControl = new DeviceAuthorizationCacheControl(userCode,
                deviceCode, client, scopes, verificationUri, verificationUriComplete, expiresIn, interval, lastAccess, status);

        
        applicationAuditLogger.sendMessage(oAuth2AuditLog);
        return null;
    }
}