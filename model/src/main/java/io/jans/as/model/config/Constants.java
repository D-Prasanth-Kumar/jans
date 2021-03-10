/*
 * Janssen Project software is available under the Apache License (2004). See http://www.apache.org/licenses/ for full text.
 *
 * Copyright (c) 2020, Janssen Project
 */
package io.jans.as.model.config;

/**
 * @author Yuriy Zabrovarnyy
 */
public class Constants {

    private Constants() {
    }

    public static final String SERVER_KEY_OF_CONFIGURATION_ENTRY = "scim_ConfigurationEntryDN";

    public static final String BASE_PROPERTIES_FILE_NAME = "jans.properties";
    public static final String LDAP_PROPERTIES_FILE_NAME = "jans-ldap.properties";
    public static final String COUCHBASE_PROPERTIES_FILE_NAME = "jans-couchbase.properties";
    public static final String SALT_FILE_NAME = "salt";
    public static final String CERTS_DIR = "certsDir";
}
