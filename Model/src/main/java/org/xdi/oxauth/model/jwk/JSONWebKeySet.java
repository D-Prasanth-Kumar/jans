/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.model.jwk;

import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.xdi.oxauth.model.crypto.signature.SignatureAlgorithm;
import org.xdi.oxauth.model.crypto.signature.SignatureAlgorithmFamily;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.xdi.oxauth.model.jwk.JWKParameter.JSON_WEB_KEY_SET;

/**
 * @author Javier Rojas Blum
 * @version June 15, 2016
 */
public class JSONWebKeySet {

    private static final Logger LOG = Logger.getLogger(JSONWebKeySet.class);

    private List<JSONWebKey> keys;

    public JSONWebKeySet() {
        keys = new ArrayList<JSONWebKey>();
    }

    public List<JSONWebKey> getKeys() {
        return keys;
    }

    public void setKeys(List<JSONWebKey> keys) {
        this.keys = keys;
    }

    public JSONWebKey getKey(String keyId) {
        for (JSONWebKey jsonWebKey : keys) {
            if (jsonWebKey.getKid().equals(keyId)) {
                return jsonWebKey;
            }
        }

        return null;
    }

    @Deprecated
    public List<JSONWebKey> getKeys(SignatureAlgorithm algorithm) {
        List<JSONWebKey> jsonWebKeys = new ArrayList<JSONWebKey>();

        if (SignatureAlgorithmFamily.RSA.equals(algorithm.getFamily())) {
            for (JSONWebKey jsonWebKey : keys) {
                if (jsonWebKey.getAlg().equals(algorithm.getName())) {
                    jsonWebKeys.add(jsonWebKey);
                }
            }
        } else if (SignatureAlgorithmFamily.EC.equals(algorithm.getFamily())) {
            for (JSONWebKey jsonWebKey : keys) {
                if (jsonWebKey.getAlg().equals(algorithm.getName())) {
                    jsonWebKeys.add(jsonWebKey);
                }
            }
        }

        Collections.sort(jsonWebKeys);
        return jsonWebKeys;
    }

    public JSONObject toJSONObject() throws JSONException {
        JSONObject jsonObj = new JSONObject();
        JSONArray keys = new JSONArray();

        for (JSONWebKey key : getKeys()) {
            JSONObject jsonKeyValue = key.toJSONObject();

            keys.put(jsonKeyValue);
        }

        jsonObj.put(JSON_WEB_KEY_SET, keys);
        return jsonObj;
    }

    @Override
    public String toString() {
        try {
            JSONObject jwks = toJSONObject();
            return jwks.toString(4);
        } catch (JSONException e) {
            LOG.error(e.getMessage(), e);
            return null;
        }
    }
}