package org.gluu.oxauth.model.ldap;

import org.gluu.persist.annotation.*;

import java.io.Serializable;
import java.util.Date;

/**
 * Object class used to save information of every CIBA request.
 *
 * @author Milton BO
 * @version May 27, 2020
 */
@DataEntry
@ObjectClass(value = "cibaRequest")
public class CIBARequest implements Serializable {

    @DN
    private String dn;

    @AttributeName(name = "authReqId")
    private String authReqId;

    @AttributeName(name = "clnId", consistency = true)
    private String clientId;

    @AttributeName(name = "usrId", consistency = true)
    private String userId;

    @AttributeName(name = "requestDate")
    private Date requestDate;

    @AttributeName(name = "exp")
    private Date expirationDate;

    @AttributeName(name = "status")
    private String status;


    public String getDn() {
        return dn;
    }

    public void setDn(String dn) {
        this.dn = dn;
    }

    public String getAuthReqId() {
        return authReqId;
    }

    public void setAuthReqId(String authReqId) {
        this.authReqId = authReqId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Date getRequestDate() {
        return requestDate;
    }

    public void setRequestDate(Date requestDate) {
        this.requestDate = requestDate;
    }

    public Date getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(Date expirationDate) {
        this.expirationDate = expirationDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CIBARequest that = (CIBARequest) o;

        if (!dn.equals(that.dn)) return false;
        if (!authReqId.equals(that.authReqId)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = dn.hashCode();
        result = 31 * result + authReqId.hashCode();
        return result;
    }
}
