package io.jans.as.client.ws.rs;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import io.jans.as.client.BaseTest;
import io.jans.as.client.GluuConfigurationClient;
import io.jans.as.client.GluuConfigurationResponse;

/**
 * Created by eugeniuparvan on 8/12/16.
 */
public class GluuConfigurationWebServiceHttpTest extends BaseTest {

    @Test
    public void requestGluuConfiguration() throws Exception {
        GluuConfigurationClient client = new GluuConfigurationClient(gluuConfigurationEndpoint);
        GluuConfigurationResponse response = client.execGluuConfiguration();

        showClient(client);
        assertEquals(response.getStatus(), 200, "Unexpected response code. Entity: " + response.getEntity());
        assertNotNull(response.getEntity(), "The entity is null");
    }
}
