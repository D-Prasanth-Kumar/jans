package io.jans.as.server.ws.rs.stat;

import io.jans.as.common.model.stat.StatEntry;
import io.jans.as.model.common.ComponentType;
import io.jans.as.model.configuration.AppConfiguration;
import io.jans.as.model.error.ErrorResponseFactory;
import io.jans.as.model.token.TokenErrorResponseType;
import io.jans.as.server.model.session.SessionClient;
import io.jans.as.server.security.Identity;
import io.jans.as.server.service.stat.StatService;
import io.jans.as.server.util.ServerUtil;
import io.jans.orm.PersistenceEntryManager;
import io.jans.orm.search.filter.Filter;
import net.agkn.hll.HLL;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Yuriy Zabrovarnyy
 */
@ApplicationScoped
@Path("/internal/stat")
public class StatWS {

    private static final int DEFAULT_WS_INTERVAL_LIMIT_IN_SECONDS = 60;

    @Inject
    private Logger log;

    @Inject
    private PersistenceEntryManager entryManager;

    @Inject
    private ErrorResponseFactory errorResponseFactory;

    @Inject
    private Identity identity;

    @Inject
    private StatService statService;

    @Inject
    private AppConfiguration appConfiguration;

    private long lastProcessedAt;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response statGet(@HeaderParam("Authorization") String authorization, @QueryParam("month") String month) {
        return stat(month);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response statPost(@HeaderParam("Authorization") String authorization, @FormParam("month") String month) {
        return stat(month);
    }

    public Response stat(String month) {
        log.debug("Attempting to request stat, month: " + month);

        errorResponseFactory.validateComponentEnabled(ComponentType.STAT);
        validateAuthorization();
        final List<String> months = validateMonth(month);

        if (!allowToRun()) {
            log.trace("Interval request limit exceeded. Request is rejected. Current interval limit: " + appConfiguration.getStatWebServiceIntervalLimitInSeconds() + " (or 60 seconds if not set).");
            throw errorResponseFactory.createWebApplicationException(Response.Status.FORBIDDEN, TokenErrorResponseType.ACCESS_DENIED, "Interval request limit exceeded.");
        }

        lastProcessedAt = System.currentTimeMillis();

        try {
            log.trace("Recognized months: " + months);
            final String responseAsStr = ServerUtil.asJson(buildResponse(months));
            log.trace("Stat: " + responseAsStr);
            return Response.ok().entity(responseAsStr).build();
        } catch (WebApplicationException e) {
            log.error(e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).type(MediaType.APPLICATION_JSON_TYPE).build();
        }
    }

    private StatResponse buildResponse(List<String> months) {
        StatResponse response = new StatResponse();
        for (String month : months) {
            final StatResponseItem responseItem = buildItem(month);
            if (responseItem != null) {
                response.getResponse().put(month, responseItem);
            }
        }

        return response;
    }

    private StatResponseItem buildItem(String month) {
        try {
            String monthlyDn = String.format("ou=%s,%s", month, statService.getBaseDn());

            final List<StatEntry> entries = entryManager.findEntries(monthlyDn, StatEntry.class, Filter.createPresenceFilter("jansId"));
            if (entries == null || entries.isEmpty()) {
                log.trace("Can't find stat entries for month: " + monthlyDn);
                return null;
            }

            final StatResponseItem responseItem = new StatResponseItem();
            responseItem.setMonthlyActiveUsers(userCardinality(entries));

            unionTokenMapIntoResponseItem(entries, responseItem);

            return responseItem;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    private void unionTokenMapIntoResponseItem(List<StatEntry> entries, StatResponseItem responseItem) {
        for (StatEntry entry : entries) {
            for (Map.Entry<String, Map<String, Long>> en : entry.getStat().getTokenCountPerGrantType().entrySet()) {
                if (en.getValue() == null) {
                    continue;
                }

                final Map<String, Long> tokenMap = responseItem.getTokenCountPerGrantType().get(en.getKey());
                if (tokenMap == null) {
                    responseItem.getTokenCountPerGrantType().put(en.getKey(), en.getValue());
                    continue;
                }

                for (Map.Entry<String, Long> tokenEntry : en.getValue().entrySet()) {
                    final Long counter = tokenMap.get(tokenEntry.getKey());
                    if (counter == null) {
                        tokenMap.put(tokenEntry.getKey(), tokenEntry.getValue());
                        continue;
                    }

                    tokenMap.put(tokenEntry.getKey(), counter + tokenEntry.getValue());
                }
            }
        }
    }

    private long userCardinality(List<StatEntry> entries) {
        final StatEntry firstEntry = entries.get(0);
        HLL hll = HLL.fromBytes(firstEntry.getUserHllData().getBytes(StandardCharsets.UTF_8));

        // Union hll
        if (entries.size() > 1) {
            for (int i = 1; i < entries.size(); i++) {
                hll.union(HLL.fromBytes(entries.get(i).getUserHllData().getBytes(StandardCharsets.UTF_8)));
            }
        }
        return hll.cardinality();
    }

    private void validateAuthorization() {
        SessionClient sessionClient = identity.getSessionClient();
        if (sessionClient == null || sessionClient.getClient() == null) {
            log.trace("Client is unknown. Skip stat processing.");
            throw errorResponseFactory.createWebApplicationException(Response.Status.UNAUTHORIZED, TokenErrorResponseType.INVALID_CLIENT, "Failed to authenticate client.");
        }
    }

    private List<String> validateMonth(String month) {
        if (StringUtils.isBlank(month)) {
            throw errorResponseFactory.createWebApplicationException(Response.Status.BAD_REQUEST, TokenErrorResponseType.INVALID_REQUEST, "`month` parameter can't be blank and should be in format yyyyMM (e.g. 202012)");
        }

        month = ServerUtil.urlDecode(month);

        List<String> months = new ArrayList<>();
        for (String m : month.split(" ")) {
            m = m.trim();
            if (m.length() == 6) {
                months.add(m);
            }
        }

        if (months.isEmpty()) {
            throw errorResponseFactory.createWebApplicationException(Response.Status.BAD_REQUEST, TokenErrorResponseType.INVALID_REQUEST, "`month` parameter can't be blank and should be in format yyyyMM (e.g. 202012)");
        }

        return months;
    }

    private boolean allowToRun() {
        int interval = appConfiguration.getStatWebServiceIntervalLimitInSeconds();
        if (interval <= 0) {
            interval = DEFAULT_WS_INTERVAL_LIMIT_IN_SECONDS;
        }

        long timerInterval = interval * 1000;

        long timeDiff = System.currentTimeMillis() - lastProcessedAt;

        return timeDiff >= timerInterval;
    }
}
