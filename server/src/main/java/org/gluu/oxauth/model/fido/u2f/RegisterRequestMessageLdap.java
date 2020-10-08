/*
 * Janssen Project software is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2020, Janssen Project
 */
package org.gluu.oxauth.model.fido.u2f;

import java.io.Serializable;
import java.util.Date;

import org.gluu.oxauth.model.fido.u2f.protocol.RegisterRequestMessage;
import io.jans.orm.annotation.AttributeName;
import io.jans.orm.annotation.JsonObject;

/**
 * U2F registration requests
 *
 * @author Yuriy Movchan
 * @version August 9, 2017
 */
public class RegisterRequestMessageLdap extends RequestMessageLdap implements Serializable {

    private static final long serialVersionUID = -2242931562244920584L;

    @JsonObject
    @AttributeName(name = "oxRequest")
    private RegisterRequestMessage registerRequestMessage;

    public RegisterRequestMessageLdap() {
    }

    public RegisterRequestMessageLdap(RegisterRequestMessage registerRequestMessage) {
        this.registerRequestMessage = registerRequestMessage;
        this.requestId = registerRequestMessage.getRequestId();
    }

    public RegisterRequestMessageLdap(String dn, String id, Date creationDate, String sessionId, String userInum,
                                      RegisterRequestMessage registerRequestMessage) {
        super(dn, id, registerRequestMessage.getRequestId(), creationDate, sessionId, userInum);
        this.registerRequestMessage = registerRequestMessage;
    }

    public RegisterRequestMessage getRegisterRequestMessage() {
        return registerRequestMessage;
    }

    public void setRegisterRequestMessage(RegisterRequestMessage registerRequestMessage) {
        this.registerRequestMessage = registerRequestMessage;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("RegisterRequestMessageLdap [id=").append(id).append(", registerRequestMessage=").append(registerRequestMessage).append(", requestId=")
                .append(requestId).append(", creationDate=").append(creationDate).append("]");
        return builder.toString();
    }

}
