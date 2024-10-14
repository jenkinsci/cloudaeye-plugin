package io.jenkins.plugins.cloudaeye;

import com.google.gson.JsonObject;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import javax.servlet.ServletException;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.logging.Logger;

/**
 * Handles the global key configuration for the plugin
 */
@Extension
public class GlobalKeyConfiguration extends GlobalConfiguration {
    private static final Logger LOGGER = Logger.getLogger(GlobalKeyConfiguration.class.getName());
    private final NotificationSender notificationSender;

    /** @return the singleton instance */
    public static GlobalKeyConfiguration get() {
        return ExtensionList.lookupSingleton(GlobalKeyConfiguration.class);
    }

    @SuppressWarnings("lgtm[jenkins/plaintext-storage]")
    private String tenantKey;
    @SuppressWarnings("lgtm[jenkins/plaintext-storage]")
    private String token;

    public GlobalKeyConfiguration() {
        // When Jenkins loads the plugin, load any saved configurations
        load();
        this.notificationSender = new NotificationSender();
    }

    // Getters and setters for keys
    public String getTenantKey() {
        return tenantKey;
    }

    @DataBoundSetter
    public void setTenantKey(String tenantKey) {
        this.tenantKey = tenantKey;
        save();
    }

    public String getToken() {
        return token;
    }

    @DataBoundSetter
    public void setToken(String token) {
        this.token = token;
        save();
    }

    /**
     * Validates the tenantKey
     * @param tenantKey CloudAEye webhook tenantKey
     * @return Valid form
     */
    public FormValidation doCheckTenantKey(@QueryParameter String tenantKey) {
        if (StringUtils.isEmpty(tenantKey)) {
            return FormValidation.warning("Please specify a valid tenant key");
        }
        return FormValidation.ok();
    }
    public FormValidation doCheckToken(@QueryParameter String token) {
        if (StringUtils.isEmpty(token)) {
            return FormValidation.warning("Please provide a valid token");
        }
        return FormValidation.ok();
    }

    /**
     * Makes a dynamic ping to test the connectivity with the CloudAEye webhook
     * @param tenantKey Unique key assigned to the tenant
     * @param token Secret token
     * @return A valid/invalid form response
     * @throws IOException
     * @throws ServletException
     */
    @SuppressWarnings("lgtm[jenkins/no-permission-check]")
    @POST
    public FormValidation doTestConnection(@QueryParameter("tenantKey") final String tenantKey,
                                           @QueryParameter("token") final String token) throws IOException, ServletException {
        try {
            // Convert the details to string
            JsonObject ping = new JsonObject();
            ping.addProperty("ping", true);
            LOGGER.info(MessageFormat.format("[#{0}] Ping payload : {1}", tenantKey, ping.toString()));
            HttpResponse response = this.notificationSender.sendDetailsToCloudAEye(ping.toString(),tenantKey,token);
            if (response.getStatusLine().getStatusCode() == 200) {
                LOGGER.info(MessageFormat.format("[#{0}] Ping successful", tenantKey));
                return FormValidation.ok("Connection successful!");
            } else {
                LOGGER.info(MessageFormat.format("[#{0}] Ping failed : {1}", tenantKey, EntityUtils.toString(response.getEntity())));
                return FormValidation.error("Connection failed! Got response: " + EntityUtils.toString(response.getEntity()));
            }
        } catch (IOException e) {
            LOGGER.info(MessageFormat.format("[#{0}] Error while trying to ping CloudAEye webhook endpoint : {1}", tenantKey, e.getMessage()));
            return FormValidation.error("Error while trying to ping CloudAEye webhook endpoint : " + e.getMessage());
        }
    }

}
