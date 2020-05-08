package org.gluu.oxauth.fido2.service.mds;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.codec.binary.Hex;
import org.gluu.oxauth.fido2.exception.Fido2RPRuntimeException;
import org.gluu.oxauth.fido2.model.auth.AuthData;
import org.gluu.oxauth.fido2.service.CertificateService;
import org.gluu.oxauth.fido2.service.DataMapperService;
import org.gluu.oxauth.fido2.service.KeyStoreCreator;
import org.gluu.oxauth.fido2.service.verifier.CommonVerifiers;
import org.gluu.oxauth.model.configuration.AppConfiguration;
import org.gluu.oxauth.model.configuration.Fido2Configuration;
import org.gluu.service.cdi.event.ApplicationInitialized;
import org.slf4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * @author Yuriy Movchan
 * @version May 08, 2020
 */
@ApplicationScoped
public class AttestationCertificateService {

	@Inject
	private Logger log;

    @Inject
    private AppConfiguration appConfiguration;

	@Inject
	private KeyStoreCreator keyStoreCreator;

	@Inject
	private CertificateService certificateService;

	@Inject
	private CommonVerifiers commonVerifiers;

	@Inject
	private MdsService mdsService;

	@Inject
	private LocalMdsService localMdsService;

    @Inject
    private DataMapperService dataMapperService;

	private Map<String, X509Certificate> rootCertificatesMap;

	public void init(@Observes @ApplicationInitialized(ApplicationScoped.class) Object init) {
        Fido2Configuration fido2Configuration = appConfiguration.getFido2Configuration();
        if (fido2Configuration == null) {
            return;
        }

        String authenticatorCertsFolder = appConfiguration.getFido2Configuration().getAuthenticatorCertsFolder();
        this.rootCertificatesMap = certificateService.getCertificatesMap(authenticatorCertsFolder);
	}

	public List<X509Certificate> getAttestationRootCertificates(JsonNode metadataNode, List<X509Certificate> attestationCertificates) {
		if (metadataNode == null || !metadataNode.has("attestationRootCertificates")) {
            List<X509Certificate> selectedRootCertificate = certificateService.selectRootCertificates(rootCertificatesMap, attestationCertificates);
			return selectedRootCertificate;
		}

		ArrayNode node = (ArrayNode) metadataNode.get("attestationRootCertificates");
		Iterator<JsonNode> iter = node.elements();
		List<String> x509certificates = new ArrayList<>();
		while (iter.hasNext()) {
			JsonNode certNode = iter.next();
			x509certificates.add(certNode.asText());
		}

		return certificateService.getCertificates(x509certificates);
	}

	public List<X509Certificate> getAttestationRootCertificates(AuthData authData, List<X509Certificate> attestationCertificates) {
		String aaguid = Hex.encodeHexString(authData.getAaguid());

		JsonNode metadataForAuthenticator = localMdsService.getAuthenticatorsMetadata(aaguid);
		if (metadataForAuthenticator == null) {
			try {
				log.info("No metadata for authenticator {}. Attempting to contact MDS", aaguid);
				JsonNode metadata = mdsService.fetchMetadata(authData.getAaguid());
				commonVerifiers.verifyThatMetadataIsValid(metadata);
				localMdsService.registerAuthenticatorsMetadata(aaguid, metadata);
				metadataForAuthenticator = metadata;
				
				return getAttestationRootCertificates(metadataForAuthenticator, attestationCertificates);
			} catch (Fido2RPRuntimeException ex) {
				log.warn("Failed to get metadaa from Fido2 meta-data server");
				
				// Store empty data to avoid try to get data again
				metadataForAuthenticator = dataMapperService.createObjectNode();
				localMdsService.registerAuthenticatorsMetadata(aaguid, metadataForAuthenticator);
			}
		}

		return getAttestationRootCertificates(metadataForAuthenticator, attestationCertificates);
	}

	public X509TrustManager populateTrustManager(AuthData authData, List<X509Certificate> attestationCertificates) {
		String aaguid = Hex.encodeHexString(authData.getAaguid());
		List<X509Certificate> trustedCertificates = getAttestationRootCertificates(authData, attestationCertificates);
		KeyStore keyStore = getCertificationKeyStore(aaguid, trustedCertificates);

		TrustManagerFactory trustManagerFactory = null;
		try {
			trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			trustManagerFactory.init(keyStore);
			TrustManager[] tms = trustManagerFactory.getTrustManagers();

			return (X509TrustManager) tms[0];
		} catch (NoSuchAlgorithmException | KeyStoreException e) {
			log.error("Unrecoverable problem with the platform", e);
			return null;
		}
	}

	private KeyStore getCertificationKeyStore(String aaguid, List<X509Certificate> certificates) {
		return keyStoreCreator.createKeyStore(aaguid, certificates);
	}

}
