package io.jenkins.plugins.cloudaeye;

import hudson.util.Secret;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.logging.Logger;

/**
 * Sends given notification payload to the CloudAEye webhook endpoint
 */
public class NotificationSender {

    private static final Logger LOGGER = Logger.getLogger(NotificationSender.class.getName());

    /**
     * The webhook endpoint to send the notifications to
     * @param tenantKey Unique key assigned to the tenant
     * @return url string
     */
    private String getEndpointByTenantKey(String tenantKey) {
        return MessageFormat.format("https://api.cloudaeye.com/rca/test/v1/tenants/{0}/jenkins/process-build", tenantKey);
    }

    /**
     * Posts the details as webhook notification to the CloudAEye endpoint
     * @param details Details to send as payload
     * @param tenantKey Unique key assigned to the tenant
     * @param token Secret token assigned to this user
     * @return HttpResponse HttpResponse
     * @throws IOException
     */
    HttpResponse sendDetailsToCloudAEye(String details, Secret tenantKey, Secret token) throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            String endpoint = getEndpointByTenantKey(tenantKey.getPlainText());
            // Set endpoint and respective auth headers
            HttpPost httpPost = new HttpPost(endpoint);
            httpPost.setHeader("Authorization", "Basic " + token.getPlainText());
            httpPost.setHeader("Content-Type", "application/json");
            // Convert the details to string
            HttpEntity payload = new StringEntity(details, ContentType.APPLICATION_JSON);
            httpPost.setEntity(payload);
            // Send the post request and return the response
            LOGGER.fine(MessageFormat.format("Sending captured build details to CloudAEye : {0}", tenantKey));
            return client.execute(httpPost);
        }
    }

}
