/*
 * Janssen Project software is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2020, Janssen Project
 */

package io.jans.as.server.dev;

import io.jans.orm.PersistenceEntryManager;
import io.jans.orm.ldap.impl.LdapEntryManagerFactory;
import io.jans.orm.ldap.operation.impl.LdapConnectionProvider;
import io.jans.util.properties.FileConfiguration;
import io.jans.util.security.PropertiesDecrypter;
import io.jans.as.common.model.registration.Client;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Properties;

/**
 * Test for manual run. Used for development purpose ONLY. Must not be run in
 * suite. ATTENTION : To make life easier must not have dependency on embedded
 * server.
 *
 * @author Yuriy Zabrovarnyy
 * @version 0.9, 26/07/2012
 */

public class Manual {

	public static String LDAP_CONF_FILE_NAME = "oxauth-ldap.properties";
	public static final String CONF_FOLDER = "conf";

	private static final String LDAP_FILE_PATH = CONF_FOLDER + File.separator + LDAP_CONF_FILE_NAME;

	public static PersistenceEntryManager MANAGER = null;

	@BeforeClass
	public void init() {
		final FileConfiguration fileConfiguration = new FileConfiguration(LDAP_FILE_PATH);
		final Properties props = PropertiesDecrypter.decryptProperties(fileConfiguration.getProperties(), "passoword");
		final LdapEntryManagerFactory ldapEntryManagerFactory = new LdapEntryManagerFactory(); 
		final LdapConnectionProvider connectionProvider = new LdapConnectionProvider(props);
		MANAGER = ldapEntryManagerFactory.createEntryManager(props);
	}

	@AfterClass
	public void destroy() {
		MANAGER.destroy();
	}

	@Test
	public void getGroupsFromClient() {
		final Client client = MANAGER.find(Client.class, "inum=@!0000!0008!7652.0000,ou=clients,o=gluu");
		System.out.println(client);
	}
}
