package io.jenkins.plugins.cloudaeye;

import com.google.gson.JsonObject;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.util.FormValidation;
import hudson.util.Secret;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

/**
 * Handles the global key configuration for the plugin
 */
@Extension
public class CloudAEyeGlobalKeyConfiguration extends GlobalConfiguration {
    private static final Logger LOGGER = Logger.getLogger(CloudAEyeGlobalKeyConfiguration.class.getName());

    /** @return the singleton instance */
    public static CloudAEyeGlobalKeyConfiguration get() {
        return ExtensionList.lookupSingleton(CloudAEyeGlobalKeyConfiguration.class);
    }

    private Secret tenantKey;
    private Secret token;

    public CloudAEyeGlobalKeyConfiguration() {
        // When Jenkins loads the plugin, load any saved configurations
        load();
    }

    // Getters and setters for keys
    public Secret getTenantKey() {
        return tenantKey;
    }

    @DataBoundSetter
    public void setTenantKey(Secret tenantKey) {
        this.tenantKey = tenantKey;
        save();
    }

    public Secret getToken() {
        return token;
    }

    @DataBoundSetter
    public void setToken(Secret token) {
        this.token = token;
        save();
    }

    /**
     * Validates the tenantKey
     * @param tenantKey CloudAEye webhook tenantKey
     * @return Valid form
     */
    public FormValidation doCheckTenantKey(@QueryParameter String tenantKey) {
        if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            return FormValidation.ok();
        }
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
    @POST
    public FormValidation doTestConnection(
            @QueryParameter("tenantKey") final Secret tenantKey, @QueryParameter("token") final Secret token)
            throws IOException, ServletException {
        try {
            // Convert the details to string
            JsonObject ping = new JsonObject();
            ping.addProperty("ping", true);
            LOGGER.fine(MessageFormat.format("[#{0}] Ping payload : {1}", tenantKey, ping.toString()));
            NotificationSender notificationSender = new NotificationSender();
            HttpResponse response = notificationSender.sendDetailsToCloudAEye(ping.toString(), tenantKey, token);
            if (response.getStatusLine().getStatusCode() == 200) {
                LOGGER.fine(MessageFormat.format("[#{0}] Ping successful", tenantKey));
                return FormValidation.ok("Connection successful!");
            } else {
                LOGGER.fine(MessageFormat.format(
                        "[#{0}] Ping failed : {1}", tenantKey, EntityUtils.toString(response.getEntity())));
                return FormValidation.error(
                        "Connection failed! Got response: " + EntityUtils.toString(response.getEntity()));
            }
        } catch (IOException e) {
            LOGGER.fine(MessageFormat.format(
                    "[#{0}] Error while trying to ping CloudAEye webhook endpoint : {1}", tenantKey, e.getMessage()));
            return FormValidation.error("Error while trying to ping CloudAEye webhook endpoint : " + e.getMessage());
        }
    }
}
