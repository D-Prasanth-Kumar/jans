/*
 * Janssen Project software is available under the Apache License (2004). See http://www.apache.org/licenses/ for full text.
 *
 * Copyright (c) 2020, Janssen Project
 */

package io.jans.as.server.comp;

import java.util.Date;
import java.util.UUID;

import javax.inject.Inject;

import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import io.jans.as.server.BaseComponentTest;
import io.jans.as.server.model.ldap.TokenLdap;
import io.jans.as.server.model.ldap.TokenType;
import io.jans.as.server.service.GrantService;
import io.jans.as.server.util.TokenHashUtil;

/**
 * @author Yuriy Zabrovarnyy
 * @author Yuriy Movchan
 * @version September 16, 2015
 */

public class GrantServiceTest extends BaseComponentTest {

	private static final String TEST_TOKEN_CODE = UUID.randomUUID().toString();

	@Inject
	private GrantService grantService;

	private static String m_clientId;

	private static TokenLdap m_tokenLdap;

	@Parameters(value = "clientId")
	@Test
	public void createTestToken(String clientId) {
		this.m_clientId = clientId;
		m_tokenLdap = createTestToken();
		grantService.persist(m_tokenLdap);
	}

	@Test(dependsOnMethods = "createTestToken")
	public void removeTestTokens() {
		final TokenLdap t = grantService.getGrantByCode(TEST_TOKEN_CODE);
		if (t != null) {
			grantService.remove(t);
		}
	}

	private TokenLdap createTestToken() {
		final String grantId = GrantService.generateGrantId();
		final String dn = grantService.buildDn(TokenHashUtil.hash(TEST_TOKEN_CODE));

		final TokenLdap t = new TokenLdap();
		t.setDn(dn);
		t.setGrantId(grantId);
		t.setClientId(m_clientId);
		t.setTokenCode(TokenHashUtil.hash(TEST_TOKEN_CODE));
		t.setTokenType(TokenType.ACCESS_TOKEN.getValue());
		t.setCreationDate(new Date());
		t.setExpirationDate(new Date());
		return t;
	}

}
