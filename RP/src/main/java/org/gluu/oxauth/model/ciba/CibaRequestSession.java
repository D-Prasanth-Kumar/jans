package org.gluu.oxauth.model.ciba;

import org.gluu.oxauth.client.BackchannelAuthenticationRequest;
import org.gluu.oxauth.client.BackchannelAuthenticationResponse;
import org.gluu.oxauth.model.common.BackchannelTokenDeliveryMode;

public class CibaRequestSession {

    private BackchannelAuthenticationRequest request;
    private BackchannelAuthenticationResponse response;
    private CibaFlowState state;
    private BackchannelTokenDeliveryMode tokenDeliveryMode;
    private String clientId;
    private String clientSecret;


    public CibaRequestSession() {

    }

    public CibaRequestSession(BackchannelAuthenticationRequest request, BackchannelAuthenticationResponse response,
                              CibaFlowState state) {
        this.request = request;
        this.response = response;
        this.state = state;
    }

    public BackchannelAuthenticationRequest getRequest() {
        return request;
    }

    public void setRequest(BackchannelAuthenticationRequest request) {
        this.request = request;
    }

    public BackchannelAuthenticationResponse getResponse() {
        return response;
    }

    public void setResponse(BackchannelAuthenticationResponse response) {
        this.response = response;
    }

    public CibaFlowState getState() {
        return state;
    }

    public void setState(CibaFlowState state) {
        this.state = state;
    }

    public BackchannelTokenDeliveryMode getTokenDeliveryMode() {
        return tokenDeliveryMode;
    }

    public void setTokenDeliveryMode(BackchannelTokenDeliveryMode tokenDeliveryMode) {
        this.tokenDeliveryMode = tokenDeliveryMode;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    @Override
    public String toString() {
        return "CibaRequestSession{" +
                "request=" + request +
                ", response=" + response +
                ", state=" + state +
                ", tokenDeliveryMode=" + tokenDeliveryMode +
                '}';
    }
}
