/*
 * Janssen Project software is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2020, Janssen Project
 */

package io.jans.as.model.crypto.signature;

import org.json.JSONException;
import org.json.JSONObject;
import io.jans.as.model.crypto.PrivateKey;
import io.jans.as.model.util.Base64Util;
import io.jans.as.model.util.StringUtils;

import static io.jans.as.model.jwk.JWKParameter.*;

import java.math.BigInteger;

/**
 * The Private Key for the Elliptic Curve Digital Signature Algorithm (ECDSA)
 *
 * @author Javier Rojas Blum
 * @version July 31, 2016
 */
public class ECDSAPrivateKey extends PrivateKey {

    private BigInteger d;

    public ECDSAPrivateKey(BigInteger d) {
        this.d = d;
    }

    public ECDSAPrivateKey(String d) {
        this.d = new BigInteger(1, Base64Util.base64urldecode(d));
    }

    public BigInteger getD() {
        return d;
    }

    public void setD(BigInteger d) {
        this.d = d;
    }

    @Override
    public JSONObject toJSONObject() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(MODULUS, JSONObject.NULL);
        jsonObject.put(EXPONENT, JSONObject.NULL);
        jsonObject.put(D, Base64Util.base64urlencodeUnsignedBigInt(d));

        return jsonObject;
    }

    @Override
    public String toString() {
        try {
            return toJSONObject().toString(4);
        } catch (JSONException e) {
            return StringUtils.EMPTY_STRING;
        } catch (Exception e) {
            return StringUtils.EMPTY_STRING;
        }
    }
}