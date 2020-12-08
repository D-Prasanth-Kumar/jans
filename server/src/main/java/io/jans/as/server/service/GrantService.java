/*
 * Janssen Project software is available under the Apache License (2004). See http://www.apache.org/licenses/ for full text.
 *
 * Copyright (c) 2020, Janssen Project
 */

package io.jans.as.server.service;

import com.google.common.collect.Lists;
import io.jans.as.model.config.StaticConfiguration;
import io.jans.as.model.configuration.AppConfiguration;
import io.jans.as.server.model.common.AuthorizationGrant;
import io.jans.as.server.model.common.CacheGrant;
import io.jans.as.server.model.ldap.TokenLdap;
import io.jans.as.server.model.ldap.TokenType;
import io.jans.as.server.util.TokenHashUtil;
import io.jans.orm.PersistenceEntryManager;
import io.jans.orm.search.filter.Filter;
import io.jans.service.CacheService;
import io.jans.service.cache.CacheConfiguration;
import io.jans.service.cache.CacheProviderType;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static io.jans.as.server.util.ServerUtil.isTrue;

/**
 * @author Yuriy Zabrovarnyy
 * @author Javier Rojas Blum
 * @version November 28, 2018
 */
@Stateless
@Named
public class GrantService {

    @Inject
    private Logger log;

    @Inject
    private PersistenceEntryManager persistenceEntryManager;

    @Inject
    private ClientService clientService;

    @Inject
    private CacheService cacheService;

    @Inject
    private StaticConfiguration staticConfiguration;

    @Inject
    private AppConfiguration appConfiguration;

    @Inject
    private CacheConfiguration cacheConfiguration;

    public static String generateGrantId() {
        return UUID.randomUUID().toString();
    }

    public String buildDn(String p_hashedToken) {
        return String.format("tknCde=%s,", p_hashedToken) + tokenBaseDn();
    }

    private String tokenBaseDn() {
        return staticConfiguration.getBaseDn().getTokens();  // ou=tokens,o=jans
    }

    public void merge(TokenLdap p_token) {
        persistenceEntryManager.merge(p_token);
    }

    public void mergeSilently(TokenLdap p_token) {
        try {
            persistenceEntryManager.merge(p_token);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private boolean shouldPutInCache(TokenType tokenType) {
        if (cacheConfiguration.getCacheProviderType() == CacheProviderType.NATIVE_PERSISTENCE) {
            return false;
        }

        switch (tokenType) {
            case ID_TOKEN:
                if (!isTrue(appConfiguration.getPersistIdTokenInLdap())) {
                    return true;
                }
            case REFRESH_TOKEN:
                if (!isTrue(appConfiguration.getPersistRefreshTokenInLdap())) {
                    return true;
                }
        }
        return false;
    }

    public void persist(TokenLdap token) {
        persistenceEntryManager.persist(token);
    }

    public void remove(TokenLdap p_token) {
        persistenceEntryManager.remove(p_token);
        log.trace("Removed token from LDAP, code: " + p_token.getTokenCode());
    }

    public void removeSilently(TokenLdap token) {
        try {
            remove(token);

            if (StringUtils.isNotBlank(token.getAuthorizationCode())) {
                cacheService.remove(CacheGrant.cacheKey(token.getAuthorizationCode(), token.getGrantId()));
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public void remove(List<TokenLdap> p_entries) {
        if (p_entries != null && !p_entries.isEmpty()) {
            for (TokenLdap t : p_entries) {
                try {
                    remove(t);
                } catch (Exception e) {
                    log.error("Failed to remove entry", e);
                }
            }
        }
    }

    public void removeSilently(List<TokenLdap> p_entries) {
        if (p_entries != null && !p_entries.isEmpty()) {
            for (TokenLdap t : p_entries) {
                removeSilently(t);
            }
        }
    }

    public void remove(AuthorizationGrant p_grant) {
        if (p_grant != null && p_grant.getTokenLdap() != null) {
            try {
                remove(p_grant.getTokenLdap());
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    public List<TokenLdap> getGrantsOfClient(String p_clientId) {
        try {
            final String baseDn = clientService.buildClientDn(p_clientId);
            return persistenceEntryManager.findEntries(baseDn, TokenLdap.class, Filter.createPresenceFilter("tknCde"));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return Collections.emptyList();
    }

    public TokenLdap getGrantByCode(String p_code) {
        Object grant = cacheService.get(TokenHashUtil.hash(p_code));
        if (grant instanceof TokenLdap) {
            return (TokenLdap) grant;
        } else {
            return load(buildDn(TokenHashUtil.hash(p_code)));
        }
    }

    private TokenLdap load(String p_tokenDn) {
        try {
            final TokenLdap entry = persistenceEntryManager.find(TokenLdap.class, p_tokenDn);
            return entry;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    public List<TokenLdap> getGrantsByGrantId(String p_grantId) {
        try {
            return persistenceEntryManager.findEntries(tokenBaseDn(), TokenLdap.class, Filter.createEqualityFilter("grtId", p_grantId));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return Collections.emptyList();
    }

    public List<TokenLdap> getGrantsByAuthorizationCode(String p_authorizationCode) {
        try {
            return persistenceEntryManager.findEntries(tokenBaseDn(), TokenLdap.class, Filter.createEqualityFilter("authzCode", TokenHashUtil.hash(p_authorizationCode)));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return Collections.emptyList();
    }

    public List<TokenLdap> getGrantsBySessionDn(String sessionDn) {
        List<TokenLdap> grants = new ArrayList<>();
        try {
            List<TokenLdap> ldapGrants = persistenceEntryManager.findEntries(tokenBaseDn(), TokenLdap.class, Filter.createEqualityFilter("ssnId", sessionDn));
            if (ldapGrants != null) {
                grants.addAll(ldapGrants);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return grants;
    }

    public void logout(String sessionDn) {
        final List<TokenLdap> tokens = getGrantsBySessionDn(sessionDn);
        if (!appConfiguration.getRemoveRefreshTokensForClientOnLogout()) {
            List<TokenLdap> refreshTokens = Lists.newArrayList();
            for (TokenLdap token : tokens) {
                if (token.getTokenTypeEnum() == TokenType.REFRESH_TOKEN) {
                    refreshTokens.add(token);
                }
            }
            if (!refreshTokens.isEmpty()) {
                log.trace("Refresh tokens are not removed on logout (because removeRefreshTokensForClientOnLogout configuration property is false)");
                tokens.removeAll(refreshTokens);
            }
        }
        removeSilently(tokens);
    }

    public void removeAllTokensBySession(String sessionDn, boolean logout) {
        removeSilently(getGrantsBySessionDn(sessionDn));
    }

    /**
     * Removes grant with particular code.
     *
     * @param code code
     */
    public void removeByCode(String code) {
        final TokenLdap t = getGrantByCode(code);
        if (t != null) {
            removeSilently(t);
        }
        cacheService.remove(CacheGrant.cacheKey(code, null));
    }

    // authorization code is saved only in cache
    public void removeAuthorizationCode(String code) {
        cacheService.remove(CacheGrant.cacheKey(code, null));
    }

    public void removeAllByAuthorizationCode(String p_authorizationCode) {
        removeSilently(getGrantsByAuthorizationCode(p_authorizationCode));
    }

    public void removeAllByGrantId(String p_grantId) {
        removeSilently(getGrantsByGrantId(p_grantId));
    }

}