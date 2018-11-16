/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.auth;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONException;
import org.gluu.jsf2.message.FacesMessages;
import org.gluu.jsf2.service.FacesService;
import org.slf4j.Logger;
import org.xdi.model.AuthenticationScriptUsageType;
import org.xdi.model.custom.script.conf.CustomScriptConfiguration;
import org.xdi.model.security.Credentials;
import org.xdi.oxauth.i18n.LanguageBean;
import org.xdi.oxauth.model.common.SessionId;
import org.xdi.oxauth.model.common.SessionIdState;
import org.xdi.oxauth.model.common.User;
import org.xdi.oxauth.model.config.Constants;
import org.xdi.oxauth.model.configuration.AppConfiguration;
import org.xdi.oxauth.model.jwt.JwtClaimName;
import org.xdi.oxauth.model.registration.Client;
import org.xdi.oxauth.model.util.Util;
import org.xdi.oxauth.security.Identity;
import org.xdi.oxauth.service.AuthenticationService;
import org.xdi.oxauth.service.ClientService;
import org.xdi.oxauth.service.RequestParameterService;
import org.xdi.oxauth.service.SessionIdService;
import org.xdi.oxauth.service.external.ExternalAuthenticationService;
import org.xdi.util.Pair;
import org.xdi.util.StringHelper;

import javax.enterprise.context.RequestScoped;
import javax.faces.application.FacesMessage;
import javax.faces.application.FacesMessage.Severity;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Authenticator component
 *
 * @author Javier Rojas Blum
 * @author Yuriy Movchan
 * @version August 23, 2018
 */
@RequestScoped
@Named
public class Authenticator {

	private static final String INVALID_SESSION_MESSAGE = "login.errorSessionInvalidMessage";

	private static final String AUTH_EXTERNAL_ATTRIBUTES = "auth_external_attributes";

	@Inject
	private Logger logger;

	@Inject
	private Identity identity;

	@Inject
	private Credentials credentials;

	@Inject
	private ClientService clientService;

	@Inject
	private SessionIdService sessionIdService;

	@Inject
	private AuthenticationService authenticationService;

	@Inject
	private ExternalAuthenticationService externalAuthenticationService;

	@Inject
	private AppConfiguration appConfiguration;

	@Inject
	private FacesContext facesContext;

	@Inject
	private ExternalContext externalContext;

	@Inject
	private FacesService facesService;

	@Inject
	private FacesMessages facesMessages;

	@Inject
	private LanguageBean languageBean;

	@Inject
	private RequestParameterService requestParameterService;

	private String authAcr;

	private Integer authStep;

	private boolean addedErrorMessage;

	/**
	 * Tries to authenticate an user, returns <code>true</code> if the
	 * authentication succeed
	 *
	 * @return Returns <code>true</code> if the authentication succeed
	 */
	public boolean authenticate() {
		HttpServletRequest servletRequest = (HttpServletRequest) facesContext.getExternalContext().getRequest();
		if (!authenticateImpl(servletRequest, true, false)) {
			return authenticationFailed();
		} else {
			return true;
		}
	}

	public String authenticateWithOutcome() {
		HttpServletRequest servletRequest = (HttpServletRequest) facesContext.getExternalContext().getRequest();
		boolean result = authenticateImpl(servletRequest, true, false);
		if (result) {
			return Constants.RESULT_SUCCESS;
		} else {
			addMessage(FacesMessage.SEVERITY_ERROR, "login.failedToAuthenticate");
			return Constants.RESULT_FAILURE;
		}

	}

	public boolean authenticateWebService(HttpServletRequest servletRequest, boolean skipPassword) {
		return authenticateImpl(servletRequest, false, skipPassword);
	}

	public boolean authenticateWebService(HttpServletRequest servletRequest) {
		return authenticateImpl(servletRequest, false, false);
	}

	public boolean authenticateImpl(HttpServletRequest servletRequest, boolean interactive, boolean skipPassword) {
		boolean authenticated = false;
		try {
			logger.trace("Authenticating ... (interactive: " + interactive + ", skipPassword: " + skipPassword
					+ ", credentials.username: " + credentials.getUsername() + ")");
			if (StringHelper.isNotEmpty(credentials.getUsername())
					&& (skipPassword || StringHelper.isNotEmpty(credentials.getPassword())) && servletRequest != null
					&& servletRequest.getRequestURI().endsWith("/token")) {
				authenticated = clientAuthentication(credentials, interactive, skipPassword);
			} else {
				if (interactive) {
					authenticated = userAuthenticationInteractive();
				} else {
					authenticated = userAuthenticationService();
				}
			}
		} catch (Exception ex) {
			logger.error(ex.getMessage(), ex);
		}

		if (authenticated) {
			logger.trace("Authentication successfully for '{}'", credentials.getUsername());
			return true;
		}

		logger.info("Authentication failed for '{}'", credentials.getUsername());
		return false;
	}

	public boolean clientAuthentication(Credentials credentials, boolean interactive, boolean skipPassword) {
		boolean isServiceUsesExternalAuthenticator = !interactive
				&& externalAuthenticationService.isEnabled(AuthenticationScriptUsageType.SERVICE);
		if (isServiceUsesExternalAuthenticator) {
			CustomScriptConfiguration customScriptConfiguration = externalAuthenticationService
					.determineCustomScriptConfiguration(AuthenticationScriptUsageType.SERVICE, 1, this.authAcr);

			if (customScriptConfiguration == null) {
				logger.error("Failed to get CustomScriptConfiguration. acr: '{}'", this.authAcr);
			} else {
				this.authAcr = customScriptConfiguration.getCustomScript().getName();

				boolean result = externalAuthenticationService.executeExternalAuthenticate(customScriptConfiguration,
						null, 1);
				logger.info("Authentication result for user '{}', result: '{}'", credentials.getUsername(), result);

				if (result) {
					Client client = authenticationService.configureSessionClient();
					showClientAuthenticationLog(client);
					return true;
				}
			}
		}

		boolean loggedIn = skipPassword;
		if (!loggedIn) {
			loggedIn = clientService.authenticate(credentials.getUsername(), credentials.getPassword());
		}
		if (loggedIn) {
			Client client = authenticationService.configureSessionClient();
			showClientAuthenticationLog(client);
			return true;
		}

		return false;
	}

	private void showClientAuthenticationLog(Client client) {
		StringBuilder sb = new StringBuilder("Authentication success for Client");
		if (StringHelper.toBoolean(appConfiguration.getLogClientIdOnClientAuthentication(), false)
				|| StringHelper.toBoolean(appConfiguration.getLogClientNameOnClientAuthentication(), false)) {
			sb.append(":");
			if (appConfiguration.getLogClientIdOnClientAuthentication()) {
				sb.append(" ").append("'").append(client.getClientId()).append("'");
			}
			if (appConfiguration.getLogClientNameOnClientAuthentication()) {
				sb.append(" ").append("('").append(client.getClientName()).append("')");
			}
		}
		logger.info(sb.toString());
	}

	private boolean userAuthenticationInteractive() {
		SessionId sessionId = sessionIdService.getSessionId();
		Map<String, String> sessionIdAttributes = sessionIdService.getSessionAttributes(sessionId);
		if (sessionIdAttributes == null) {
			logger.error("Failed to get session attributes");
			authenticationFailedSessionInvalid();
			return false;
		}

		// Set current state into identity to allow use in login form and
		// authentication scripts
		identity.setSessionId(sessionId);

		initCustomAuthenticatorVariables(sessionIdAttributes);
		boolean useExternalAuthenticator = externalAuthenticationService
				.isEnabled(AuthenticationScriptUsageType.INTERACTIVE);
		if (useExternalAuthenticator && !StringHelper.isEmpty(this.authAcr)) {
			initCustomAuthenticatorVariables(sessionIdAttributes);
			if ((this.authStep == null) || StringHelper.isEmpty(this.authAcr)) {
				logger.error("Failed to determine authentication mode");
				authenticationFailedSessionInvalid();
				return false;
			}

			CustomScriptConfiguration customScriptConfiguration = externalAuthenticationService
					.getCustomScriptConfiguration(AuthenticationScriptUsageType.INTERACTIVE, this.authAcr);
			if (customScriptConfiguration == null) {
				logger.error("Failed to get CustomScriptConfiguration for acr: '{}', auth_step: '{}'", this.authAcr,
						this.authStep);
				return false;
			}

			// Check if all previous steps had passed
			boolean passedPreviousSteps = isPassedPreviousAuthSteps(sessionIdAttributes, this.authStep);
			if (!passedPreviousSteps) {
				logger.error("There are authentication steps not marked as passed. acr: '{}', auth_step: '{}'",
						this.authAcr, this.authStep);
				return false;
			}

			// Restore identity working parameters from session
			setIdentityWorkingParameters(sessionIdAttributes);

			boolean result = externalAuthenticationService.executeExternalAuthenticate(customScriptConfiguration,
					externalContext.getRequestParameterValuesMap(), this.authStep);
			logger.debug("Authentication result for user '{}'. auth_step: '{}', result: '{}', credentials: '{}'",
					credentials.getUsername(), this.authStep, result, System.identityHashCode(credentials));

			int overridenNextStep = -1;
			logger.info("#########################################################################");
			logger.info("#########################################################################");
			logger.info("#########################################################################");
			logger.info("#########################################################################");
			logger.info("++++++++++++++++++++++++++++++++++++++++++CURRENT ACR:" + this.authAcr);
			logger.info("++++++++++++++++++++++++++++++++++++++++++CURRENT STEO:" + this.authStep);
			int apiVersion = externalAuthenticationService.executeExternalGetApiVersion(customScriptConfiguration);
			if (apiVersion > 1) {
				logger.trace("According to API version script supports steps overriding");
				overridenNextStep = externalAuthenticationService.getNextStep(customScriptConfiguration,
						externalContext.getRequestParameterValuesMap(), this.authStep);
				logger.debug("Get next step from script: '{}'", overridenNextStep);
			}

			if (!result && (overridenNextStep == -1)) {
				// Force session lastUsedAt update if authentication attempt is failed
				sessionIdService.updateSessionId(sessionId);
				return false;
			}

			boolean overrideCurrentStep = false;
			if (overridenNextStep > -1) {
				overrideCurrentStep = true;
				// Reload session id
				sessionId = sessionIdService.getSessionId();

				// Reset to specified step
				sessionIdService.resetToStep(sessionId, overridenNextStep);

				this.authStep = overridenNextStep;
				logger.info("Authentication reset to step : '{}'", this.authStep);
			}

			// Update parameters map to allow access it from count
			// authentication steps method
			updateExtraParameters(customScriptConfiguration, this.authStep + 1, sessionIdAttributes);

			// Determine count authentication methods
			int countAuthenticationSteps = externalAuthenticationService
					.executeExternalGetCountAuthenticationSteps(customScriptConfiguration);

			// Reload from LDAP to make sure that we are updating latest session
			// attributes
			sessionId = sessionIdService.getSessionId();
			sessionIdAttributes = sessionIdService.getSessionAttributes(sessionId);

			// Prepare for next step
			if ((this.authStep < countAuthenticationSteps) || overrideCurrentStep) {
				int nextStep;
				if (overrideCurrentStep) {
					nextStep = overridenNextStep;
				} else {
					nextStep = this.authStep + 1;
				}

				String redirectTo = externalAuthenticationService
						.executeExternalGetPageForStep(customScriptConfiguration, nextStep);
				if (StringHelper.isEmpty(redirectTo) || redirectTo == null) {
					redirectTo = "/error.xhtml";
				}

				// Store/Update extra parameters in session attributes map
				updateExtraParameters(customScriptConfiguration, nextStep, sessionIdAttributes);

				if (!overrideCurrentStep) {
					// Update auth_step
					sessionIdAttributes.put("auth_step", Integer.toString(nextStep));

					// Mark step as passed
					markAuthStepAsPassed(sessionIdAttributes, this.authStep);
				}

				if (sessionId != null) {
					boolean updateResult = updateSession(sessionId, sessionIdAttributes);
					if (!updateResult) {
						return false;
					}
				}

				logger.trace("Redirect to page: '{}'", redirectTo);
				facesService.redirectWithExternal(redirectTo, null);

				return true;
			}

			if (this.authStep == countAuthenticationSteps) {
				SessionId eventSessionId = authenticationService.configureSessionUser(sessionId, sessionIdAttributes);

				authenticationService.quietLogin(credentials.getUsername());

				// Redirect to authorization workflow
				logger.debug("Sending event to trigger user redirection: '{}'", credentials.getUsername());
				authenticationService.onSuccessfulLogin(eventSessionId);

				logger.info("Authentication success for User: '{}'", credentials.getUsername());
				return true;
			}
		} else {
			if (StringHelper.isNotEmpty(credentials.getUsername())) {
				boolean authenticated = authenticationService.authenticate(credentials.getUsername(),
						credentials.getPassword());
				if (authenticated) {
					SessionId eventSessionId = authenticationService.configureSessionUser(sessionId,
							sessionIdAttributes);

					// Redirect to authorization workflow
					logger.debug("Sending event to trigger user redirection: '{}'", credentials.getUsername());
					authenticationService.onSuccessfulLogin(eventSessionId);
				} else {
					// Force session lastUsedAt update if authentication attempt is failed
					sessionIdService.updateSessionId(sessionId);
				}

				logger.info("Authentication success for User: '{}'", credentials.getUsername());
				return true;
			}
		}

		return false;
	}

	private boolean updateSession(SessionId sessionId, Map<String, String> sessionIdAttributes) {
		sessionId.setSessionAttributes(sessionIdAttributes);
		boolean updateResult = sessionIdService.updateSessionId(sessionId, true, true, true);
		if (!updateResult) {
			logger.debug("Failed to update session entry: '{}'", sessionId.getId());
			return false;
		}

		return true;
	}

	private boolean userAuthenticationService() {
		if (externalAuthenticationService.isEnabled(AuthenticationScriptUsageType.SERVICE)) {
			CustomScriptConfiguration customScriptConfiguration = externalAuthenticationService
					.determineCustomScriptConfiguration(AuthenticationScriptUsageType.SERVICE, 1, this.authAcr);

			if (customScriptConfiguration == null) {
				logger.error("Failed to get CustomScriptConfiguration. auth_step: '{}', acr: '{}'", this.authStep,
						this.authAcr);
			} else {
				this.authAcr = customScriptConfiguration.getName();

				boolean result = externalAuthenticationService.executeExternalAuthenticate(customScriptConfiguration,
						null, 1);
				logger.info("Authentication result for '{}'. auth_step: '{}', result: '{}'", credentials.getUsername(),
						this.authStep, result);

				if (result) {
					authenticationService.configureEventUser();

					logger.info("Authentication success for User: '{}'", credentials.getUsername());
					return true;
				}
				logger.info("Authentication failed for User: '{}'", credentials.getUsername());
			}
		}

		if (StringHelper.isNotEmpty(credentials.getUsername())) {
			boolean authenticated = authenticationService.authenticate(credentials.getUsername(),
					credentials.getPassword());
			if (authenticated) {
				authenticationService.configureEventUser();

				logger.info("Authentication success for User: '{}'", credentials.getUsername());
				return true;
			}
			logger.info("Authentication failed for User: '{}'", credentials.getUsername());
		}

		return false;
	}

	private void updateExtraParameters(CustomScriptConfiguration customScriptConfiguration, final int step,
			Map<String, String> sessionIdAttributes) {
		List<String> extraParameters = externalAuthenticationService
				.executeExternalGetExtraParametersForStep(customScriptConfiguration, step);

		// Load extra parameters set
		Map<String, String> authExternalAttributes = getExternalScriptExtraParameters(sessionIdAttributes);

		if (extraParameters != null) {
			for (String extraParameter : extraParameters) {
				if (authenticationService.isParameterExists(extraParameter)) {
					Pair<String, String> extraParameterValueWithType = requestParameterService
							.getParameterValueWithType(extraParameter);
					String extraParameterValue = extraParameterValueWithType.getFirst();
					String extraParameterType = extraParameterValueWithType.getSecond();

					// Store parameter name and value
					sessionIdAttributes.put(extraParameter, extraParameterValue);

					// Store parameter name and type
					authExternalAttributes.put(extraParameter, extraParameterType);
				}
			}
		}

		// Store identity working parameters in session
		setExternalScriptExtraParameters(sessionIdAttributes, authExternalAttributes);
	}

	private Map<String, String> getExternalScriptExtraParameters(Map<String, String> sessionIdAttributes) {
		String authExternalAttributesString = sessionIdAttributes.get(AUTH_EXTERNAL_ATTRIBUTES);
		Map<String, String> authExternalAttributes = new HashMap<String, String>();
		try {
			authExternalAttributes = Util.jsonObjectArrayStringAsMap(authExternalAttributesString);
		} catch (JSONException ex) {
			logger.error("Failed to convert JSON array of auth_external_attributes to Map<String, String>");
		}

		return authExternalAttributes;
	}

	private void setExternalScriptExtraParameters(Map<String, String> sessionIdAttributes,
			Map<String, String> authExternalAttributes) {
		String authExternalAttributesString = null;
		try {
			authExternalAttributesString = Util.mapAsString(authExternalAttributes);
		} catch (JSONException ex) {
			logger.error("Failed to convert Map<String, String> of auth_external_attributes to JSON array");
		}

		sessionIdAttributes.put(AUTH_EXTERNAL_ATTRIBUTES, authExternalAttributesString);
	}

	private void clearExternalScriptExtraParameters(Map<String, String> sessionIdAttributes) {
		Map<String, String> authExternalAttributes = getExternalScriptExtraParameters(sessionIdAttributes);

		for (String authExternalAttribute : authExternalAttributes.keySet()) {
			sessionIdAttributes.remove(authExternalAttribute);
		}

		sessionIdAttributes.remove(AUTH_EXTERNAL_ATTRIBUTES);
	}

	private void setIdentityWorkingParameters(Map<String, String> sessionIdAttributes) {
		Map<String, String> authExternalAttributes = getExternalScriptExtraParameters(sessionIdAttributes);

		HashMap<String, Object> workingParameters = identity.getWorkingParameters();
		for (Entry<String, String> authExternalAttributeEntry : authExternalAttributes.entrySet()) {
			String authExternalAttributeName = authExternalAttributeEntry.getKey();
			String authExternalAttributeType = authExternalAttributeEntry.getValue();

			if (sessionIdAttributes.containsKey(authExternalAttributeName)) {
				String authExternalAttributeValue = sessionIdAttributes.get(authExternalAttributeName);
				Object typedValue = requestParameterService.getTypedValue(authExternalAttributeValue,
						authExternalAttributeType);

				workingParameters.put(authExternalAttributeName, typedValue);
			}
		}
	}

	public String prepareAuthenticationForStep() {
		String result = prepareAuthenticationForStepImpl();

		if (Constants.RESULT_SUCCESS.equals(result)) {
		} else if (Constants.RESULT_FAILURE.equals(result)) {
			addMessage(FacesMessage.SEVERITY_ERROR, "login.failedToAuthenticate");
		} else if (Constants.RESULT_NO_PERMISSIONS.equals(result)) {
			addMessage(FacesMessage.SEVERITY_ERROR, "login.youDontHavePermission");
		} else if (Constants.RESULT_EXPIRED.equals(result)) {
			addMessage(FacesMessage.SEVERITY_ERROR, INVALID_SESSION_MESSAGE);
		}

		return result;
	}

	private String prepareAuthenticationForStepImpl() {
		SessionId sessionId = sessionIdService.getSessionId();
		Map<String, String> sessionIdAttributes = sessionIdService.getSessionAttributes(sessionId);
		if (sessionIdAttributes == null) {
			logger.error("Failed to get attributes from session");
			return Constants.RESULT_EXPIRED;
		}

		// Set current state into identity to allow use in login form and
		// authentication scripts
		identity.setSessionId(sessionId);

		if (!externalAuthenticationService.isEnabled(AuthenticationScriptUsageType.INTERACTIVE)) {
			return Constants.RESULT_SUCCESS;
		}

		initCustomAuthenticatorVariables(sessionIdAttributes);
		if (StringHelper.isEmpty(this.authAcr)) {
			return Constants.RESULT_SUCCESS;
		}

		if ((this.authStep == null) || (this.authStep < 1)) {
			return Constants.RESULT_NO_PERMISSIONS;
		}

		CustomScriptConfiguration customScriptConfiguration = externalAuthenticationService
				.getCustomScriptConfiguration(AuthenticationScriptUsageType.INTERACTIVE, this.authAcr);
		if (customScriptConfiguration == null) {
			logger.error("Failed to get CustomScriptConfiguration. auth_step: '{}', acr: '{}'", this.authStep,
					this.authAcr);
			return Constants.RESULT_FAILURE;
		}

		String currentauthAcr = customScriptConfiguration.getName();

		customScriptConfiguration = externalAuthenticationService.determineExternalAuthenticatorForWorkflow(
				AuthenticationScriptUsageType.INTERACTIVE, customScriptConfiguration);
		if (customScriptConfiguration == null) {
			return Constants.RESULT_FAILURE;
		} else {
			String determinedauthAcr = customScriptConfiguration.getName();
			if (!StringHelper.equalsIgnoreCase(currentauthAcr, determinedauthAcr)) {
				// Redirect user to alternative login workflow
				String redirectTo = externalAuthenticationService
						.executeExternalGetPageForStep(customScriptConfiguration, this.authStep);

				if (StringHelper.isEmpty(redirectTo)) {
					redirectTo = "/login.xhtml";
				}

				CustomScriptConfiguration determinedCustomScriptConfiguration = externalAuthenticationService
						.getCustomScriptConfiguration(AuthenticationScriptUsageType.INTERACTIVE, determinedauthAcr);
				if (determinedCustomScriptConfiguration == null) {
					logger.error("Failed to get determined CustomScriptConfiguration. auth_step: '{}', acr: '{}'",
							this.authStep, this.authAcr);
					return Constants.RESULT_FAILURE;
				}

				logger.debug("Redirect to page: '{}'. Force to use acr: '{}'", redirectTo, determinedauthAcr);

				determinedauthAcr = determinedCustomScriptConfiguration.getName();
				String determinedAuthLevel = Integer.toString(determinedCustomScriptConfiguration.getLevel());

				sessionIdAttributes.put("acr", determinedauthAcr);
				sessionIdAttributes.put("auth_level", determinedAuthLevel);
				sessionIdAttributes.put("auth_step", Integer.toString(1));

				// Remove old session parameters from session
				clearExternalScriptExtraParameters(sessionIdAttributes);

				if (sessionId != null) {
					boolean updateResult = updateSession(sessionId, sessionIdAttributes);
					if (!updateResult) {
						return Constants.RESULT_EXPIRED;
					}
				}

				facesService.redirectWithExternal(redirectTo, null);

				return Constants.RESULT_SUCCESS;
			}
		}

		// Check if all previous steps had passed
		boolean passedPreviousSteps = isPassedPreviousAuthSteps(sessionIdAttributes, this.authStep);
		if (!passedPreviousSteps) {
			logger.error("There are authentication steps not marked as passed. acr: '{}', auth_step: '{}'",
					this.authAcr, this.authStep);
			return Constants.RESULT_FAILURE;
		}

		// Restore identity working parameters from session
		setIdentityWorkingParameters(sessionIdAttributes);

		Boolean result = externalAuthenticationService.executeExternalPrepareForStep(customScriptConfiguration,
				externalContext.getRequestParameterValuesMap(), this.authStep);
		if ((result != null) && result) {
			// Store/Update extra parameters in session attributes map
			updateExtraParameters(customScriptConfiguration, this.authStep, sessionIdAttributes);

			if (sessionId != null) {
				boolean updateResult = updateSession(sessionId, sessionIdAttributes);
				if (!updateResult) {
					return Constants.RESULT_FAILURE;
				}
			}

			return Constants.RESULT_SUCCESS;
		} else {
			return Constants.RESULT_FAILURE;
		}
	}

	public boolean authenticateBySessionId(String p_sessionId) {
		if (StringUtils.isNotBlank(p_sessionId) && appConfiguration.getSessionIdEnabled()) {
			try {
				SessionId sessionId = sessionIdService.getSessionId(p_sessionId);
				return authenticateBySessionId(sessionId);
			} catch (Exception e) {
				logger.trace(e.getMessage(), e);
			}
		}

		return false;
	}

	public boolean authenticateBySessionId(SessionId sessionId) {
		if (sessionId == null) {
			return false;
		}
		String p_sessionId = sessionId.getId();

		logger.trace("authenticateBySessionId, sessionId = '{}', session = '{}', state= '{}'", p_sessionId, sessionId,
				sessionId.getState());
		// IMPORTANT : authenticate by session id only if state of session is
		// authenticated!
		if (SessionIdState.AUTHENTICATED == sessionId.getState()) {
			final User user = authenticationService.getUserOrRemoveSession(sessionId);
			if (user != null) {
				try {
					authenticationService.quietLogin(user.getUserId());

					authenticationService.configureEventUser(sessionId);
				} catch (Exception e) {
					logger.trace(e.getMessage(), e);
				}

				return true;
			}
		}

		return false;
	}

	private void initCustomAuthenticatorVariables(Map<String, String> sessionIdAttributes) {
		if (sessionIdAttributes == null) {
			logger.error("Failed to restore attributes from session attributes");
			return;
		}

		this.authStep = StringHelper.toInteger(sessionIdAttributes.get("auth_step"), null);
		this.authAcr = sessionIdAttributes.get(JwtClaimName.AUTHENTICATION_CONTEXT_CLASS_REFERENCE);
	}

	private boolean authenticationFailed() {
		if (!this.addedErrorMessage) {
			if (this.authAcr.equalsIgnoreCase("twilio_sms") && this.authStep == 2) {
				facesMessages.add(FacesMessage.SEVERITY_ERROR, "Incorrect twilio code,Please try again");
			} else {
				addMessage(FacesMessage.SEVERITY_ERROR, "login.errorMessage");
			}

		}
		return false;
	}

	private void authenticationFailedSessionInvalid() {
		this.addedErrorMessage = true;
		addMessage(FacesMessage.SEVERITY_ERROR, INVALID_SESSION_MESSAGE);
		facesService.redirect("/error.xhtml");
	}

	private void markAuthStepAsPassed(Map<String, String> sessionIdAttributes, Integer authStep) {
		String key = String.format("auth_step_passed_%d", authStep);
		sessionIdAttributes.put(key, Boolean.TRUE.toString());
	}

	private boolean isAuthStepPassed(Map<String, String> sessionIdAttributes, Integer authStep) {
		String key = String.format("auth_step_passed_%d", authStep);
		if (sessionIdAttributes.containsKey(key) && Boolean.parseBoolean(sessionIdAttributes.get(key))) {
			return true;
		}

		return false;
	}

	private boolean isPassedPreviousAuthSteps(Map<String, String> sessionIdAttributes, Integer authStep) {
		for (int i = 1; i < authStep; i++) {
			boolean isAuthStepPassed = isAuthStepPassed(sessionIdAttributes, i);
			if (!isAuthStepPassed) {
				return false;
			}
		}

		return true;
	}

	public void configureSessionClient(Client client) {
		authenticationService.configureSessionClient(client);
	}

	public void addMessage(Severity severity, String summary) {
		String message = languageBean.getMessage(summary);
		facesMessages.add(severity, message);
	}

	public String getMaskMobilenumber(String mobile_number) {
		String phone = (String) identity.getWorkingParameter("receiver");
		if (phone == null || phone.isEmpty()) {
			phone = identity.getSessionId().getSessionAttributes().get("receiver");
		}
		return phone == null ? "you." : phone;
	}
}
