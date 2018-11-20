/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.model.jwe;

import com.nimbusds.jose.JWEDecrypter;
import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton;
import com.nimbusds.jose.crypto.factories.DefaultJWEDecrypterFactory;
import com.nimbusds.jwt.EncryptedJWT;
import org.xdi.oxauth.model.crypto.encryption.BlockEncryptionAlgorithm;
import org.xdi.oxauth.model.crypto.encryption.KeyEncryptionAlgorithm;
import org.xdi.oxauth.model.crypto.signature.RSAPrivateKey;
import org.xdi.oxauth.model.exception.InvalidJweException;
import org.xdi.oxauth.model.exception.InvalidJwtException;
import org.xdi.oxauth.model.jwt.JwtClaims;
import org.xdi.oxauth.model.jwt.JwtHeader;
import org.xdi.oxauth.model.jwt.JwtHeaderName;
import org.xdi.oxauth.model.util.SecurityProviderUtility;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.util.Arrays;

/**
 * @author Javier Rojas Blum
 * @version November 20, 2018
 */
public class JweDecrypterImpl extends AbstractJweDecrypter {

    private static final DefaultJWEDecrypterFactory DECRYPTER_FACTORY = new DefaultJWEDecrypterFactory();

    private PrivateKey privateKey;
    private RSAPrivateKey rsaPrivateKey;
    private byte[] sharedSymmetricKey;

    public JweDecrypterImpl(byte[] sharedSymmetricKey) {
        if (sharedSymmetricKey != null) {
            this.sharedSymmetricKey = sharedSymmetricKey.clone();
        }
    }

    public JweDecrypterImpl(RSAPrivateKey rsaPrivateKey) {
        this.rsaPrivateKey = rsaPrivateKey;
    }

    public JweDecrypterImpl(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    @Override
    public Jwe decrypt(String encryptedJwe) throws InvalidJweException {
        try {
            String[] jweParts = encryptedJwe.split("\\.");
            if (jweParts.length != 5) {
                throw new InvalidJwtException("Invalid JWS format.");
            }

            String encodedHeader = jweParts[0];
            String encodedEncryptedKey = jweParts[1];
            String encodedInitializationVector = jweParts[2];
            String encodedCipherText = jweParts[3];
            String encodedIntegrityValue = jweParts[4];

            Jwe jwe = new Jwe();
            jwe.setEncodedHeader(encodedHeader);
            jwe.setEncodedEncryptedKey(encodedEncryptedKey);
            jwe.setEncodedInitializationVector(encodedInitializationVector);
            jwe.setEncodedCiphertext(encodedCipherText);
            jwe.setEncodedIntegrityValue(encodedIntegrityValue);
            jwe.setHeader(new JwtHeader(encodedHeader));

            EncryptedJWT encryptedJwt = EncryptedJWT.parse(encryptedJwe);

            setKeyEncryptionAlgorithm(KeyEncryptionAlgorithm.fromName(jwe.getHeader().getClaimAsString(JwtHeaderName.ALGORITHM)));
            setBlockEncryptionAlgorithm(BlockEncryptionAlgorithm.fromName(jwe.getHeader().getClaimAsString(JwtHeaderName.ENCRYPTION_METHOD)));

            final KeyEncryptionAlgorithm keyEncryptionAlgorithm = getKeyEncryptionAlgorithm();
            Key encriptionKey = null;
            if (keyEncryptionAlgorithm == KeyEncryptionAlgorithm.RSA1_5 || keyEncryptionAlgorithm == KeyEncryptionAlgorithm.RSA_OAEP) {
                encriptionKey = privateKey;
            } else if (keyEncryptionAlgorithm == KeyEncryptionAlgorithm.A128KW || keyEncryptionAlgorithm == KeyEncryptionAlgorithm.A256KW) {
                if (sharedSymmetricKey == null) {
                    throw new InvalidJweException("The shared symmetric key is null");
                }
                if (sharedSymmetricKey.length != 16) { // 128 bit padding
                    MessageDigest sha = MessageDigest.getInstance("SHA-256");
                    sharedSymmetricKey = sha.digest(sharedSymmetricKey);
                    sharedSymmetricKey = Arrays.copyOf(sharedSymmetricKey, 16);
                }
                encriptionKey = new SecretKeySpec(sharedSymmetricKey, 0, sharedSymmetricKey.length, "AES");
            } else {
                throw new InvalidJweException("The key encryption algorithm is not supported");
            }

            JWEDecrypter decrypter = DECRYPTER_FACTORY.createJWEDecrypter(encryptedJwt.getHeader(), encriptionKey);
            decrypter.getJCAContext().setProvider(SecurityProviderUtility.getInstance());
            encryptedJwt.decrypt(decrypter);
            final String base64encodedPayload = encryptedJwt.getPayload().toString();
            jwe.setClaims(new JwtClaims(base64encodedPayload));

            return jwe;
        } catch (Exception e) {
            throw new InvalidJweException(e);
        }
    }
}