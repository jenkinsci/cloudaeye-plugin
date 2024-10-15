package io.jenkins.plugins.cloudaeye;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.console.AnnotatedLargeText;
import hudson.model.*;
import hudson.scm.ChangeLogSet;
import hudson.tasks.*;
import java.io.IOException;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.tasks.SimpleBuildStep;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Records all failed builds and exports relevant logs and metadata to CloudAEye
 */
public class CloudAEyeNotifications extends Recorder implements SimpleBuildStep {

    private static final Logger LOGGER = Logger.getLogger(CloudAEyeNotifications.class.getName());
    // CloudAEye endpoint to which the run details need to be notified
    @SuppressWarnings("lgtm[jenkins/plaintext-storage]")
    private final String tenantKey;
    // Secret token provided by CloudAEye
    @SuppressWarnings("lgtm[jenkins/plaintext-storage]")
    private final String token;
    // Enables sending logs to CloudAEye endpoint
    private final boolean enableExport;

    @DataBoundConstructor
    public CloudAEyeNotifications(boolean enableExport) {
        this.enableExport = enableExport;
        // Access the global configuration class
        GlobalKeyConfiguration config = GlobalKeyConfiguration.get();
        this.tenantKey = config.getTenantKey();
        this.token = config.getToken();
    }

    /**
     * Returns the CloudAEye webhook endpoint
     * @return CloudAEye endpoint URL
     */
    public String getTenantKey() {
        return tenantKey;
    }

    /**
     * Returns the token
     * @return CloudAEye secret token
     */
    public String getToken() {
        return token;
    }
    /**
     * Returns the enableExport value
     * @return true or false
     */
    public boolean getEnableExport() {
        return enableExport;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public void perform(
            @NonNull Run<?, ?> run,
            @NonNull FilePath workspace,
            @NonNull EnvVars env,
            @NonNull Launcher launcher,
            @NonNull TaskListener listener)
            throws InterruptedException, IOException {

        // Collect the entire run payload to be sent to CloudAEye
        JsonObject buildDetails = new JsonObject();
        LOGGER.info("Received run notification for run : " + run.getNumber());
        if(!this.getEnableExport()){
            LOGGER.info(MessageFormat.format("[#{0}] Exporting to CloudAEye is not enabled. Skipping export", run.getNumber()));
        }
        /*
         JSON Structure:
           {
              "job": {
                  "name":"rca-traces-api",
                  "id":"job/rca-traces-api/",
                  "buildNumber":17,
                  "duration":0,
                  "startTime": 1725957396354,
                  "endTime": 1725957486343
                  "url":"job/rca-traces-api/17/",
                  "logs": [],
                  "status: "failed"
              },
              "source": {
                  "branch":"origin/dev",
                  "commit":"b25947ba1290735eac9d34701fe85b2f5ca6d045",
                  "url":"https://github.com/CloudAEye/rca-traces-api",
                  "prId": "",
                  "prTarget": "",
                  "changeLog": {
                      "message": "",
                      "annotatedMessage": "",
                      "filePaths": []
                  }
              }
           }
        */
        // If run is not failed then skip further processing
        Result buildResult = run.getResult();
        if (!(buildResult == Result.SUCCESS || buildResult == Result.FAILURE)) {
            LOGGER.info(MessageFormat.format("[#{0}] Build status is neither success nor failure. Further processing skipped", run.getNumber()));
        }
        try {
            /*
             * Collect job metadata and logs
             */
            JsonObject job = new JsonObject();
            EnvVars envVars = run.getEnvironment(listener);
            job.addProperty("startTime", run.getStartTimeInMillis());
            long currentTime = System.currentTimeMillis();
            long duration = (long)(Math.ceil(currentTime - run.getStartTimeInMillis()) / 1000);
            job.addProperty("endTime", currentTime);
            job.addProperty("name", run.getParent().getFullName());
            job.addProperty("id", run.getParent().getUrl());
            job.addProperty("buildNumber", run.getNumber());
            job.addProperty("duration", duration);
            job.addProperty("url", run.getUrl());
            job.addProperty("status", buildResult == Result.SUCCESS ? "success": "failure");

            /*
             Collect logs as list of strings
             */
            LOGGER.info(MessageFormat.format("[#{0}] Extracting run logs", run.getNumber()));
            job.add("logs", extractRunLogs(run.getNumber(), run.getLogText()));

            buildDetails.add("job", job);

            /*
               Collect git source details
            */
            JsonObject source = new JsonObject();
            source.addProperty("url", envVars.get("GIT_URL"));
            /*
            Collect PR details (if event is PR)
            */
            if (envVars.containsKey("CHANGE_ID") || envVars.containsKey("ghprbPullId")) {
                LOGGER.info(MessageFormat.format("[#{0}] Identified git event: PR. Extracting details of the PR", run.getNumber()));
                source.addProperty("eventType", "PR");
                if(envVars.containsKey("CHANGE_ID")){
                    source.addProperty("prId", envVars.get("CHANGE_ID"));
                    source.addProperty("prSourceBranch", envVars.get("CHANGE_BRANCH"));
                    source.addProperty("prTargetBranch", envVars.get("CHANGE_TARGET"));
                    source.addProperty("prLink", envVars.get("CHANGE_URL"));
                } else {
                    source.addProperty("prId", envVars.get("ghprbPullId"));
                    source.addProperty("prSourceBranch", envVars.get("ghprbSourceBranch"));
                    source.addProperty("prTargetBranch", envVars.get("ghprbTargetBranch"));
                    source.addProperty("prLink", envVars.get("ghprbPullLink"));
                }
            } else if (envVars.containsKey("GIT_BRANCH")) {
                LOGGER.info(MessageFormat.format("[#{0}] Identified git event: PUSH. Extracting details of the PR", run.getNumber()));
                source.addProperty("eventType", "PUSH");
                /*
                Collect git commit and branch details (if git event is push)
                 */
                source.addProperty("branch", envVars.get("GIT_BRANCH"));
                source.addProperty("commit", envVars.get("GIT_COMMIT"));
                source.addProperty("prevCommit", envVars.get("GIT_PREVIOUS_COMMIT"));
            } else {
                LOGGER.info(MessageFormat.format("[#{0}] Unidentified git event", run.getNumber()));
                source.addProperty("eventType", "OTHER");
            }

            /*
                Collect file log changes
             */
            if (run instanceof WorkflowRun || run instanceof AbstractBuild<?, ?> ){
                // Extract change log for current build
                JsonArray cumulativeChangeLogs = new JsonArray();
                // If current build is a failure
                if(buildResult == Result.FAILURE) {
                    LOGGER.info(MessageFormat.format("[#{0}] Current build is a failure, collect all change logs till last successful build", run.getNumber()));
                    // Get details of last build that succeeded
                    Run<?, ?> previousSuccessfulRun = run.getPreviousSuccessfulBuild();
                    if (previousSuccessfulRun != null) {
                        LOGGER.info(MessageFormat.format("[#{0}] Previous successful build : {1}", run.getNumber(), previousSuccessfulRun.getNumber()));
                        // Collect change logs for all runs between the current and last successful one
                        Run<?, ?> r = run;
                        while (r != null && (r.getNumber() >= previousSuccessfulRun.getNumber())) {
                            JsonArray runChangeLogs = this.extractChangeLogsForRun(run.getNumber(), r);
                            cumulativeChangeLogs.addAll(runChangeLogs);
                            r = r.getPreviousBuild();
                        }
                    }
                } else {
                    LOGGER.info(MessageFormat.format("[#{0}] Current build is a success, collect change logs of current build", run.getNumber()));
                    cumulativeChangeLogs = this.extractChangeLogsForRun(run.getNumber(), run);
                }
                // Add change log details to the parent object
                source.add("changeLog", cumulativeChangeLogs);
            }

            // Add source to build details
            buildDetails.add("source", source);
            LOGGER.info(MessageFormat.format("[#{0}] Build details successfully captured : {1}", run.getNumber(), buildDetails));

            // Export the extracted details to CloudAEye
            sendDetailsToCloudAEye(run.getNumber(), buildDetails.toString(), this.getTenantKey(), this.getToken());

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Exports the run metadata and logs to CloudAEye
     *
     * @param details   Build details
     * @param tenantKey Tenant key provided by CloudAEye
     * @param token     Secret token provided by CloudAEye
     */
    private void sendDetailsToCloudAEye(int buildNumber, String details, String tenantKey, String token) {
        try {
            NotificationSender notificationSender = new NotificationSender();
            HttpResponse response = notificationSender.sendDetailsToCloudAEye(details,tenantKey,token);
            if (response.getStatusLine().getStatusCode() == 200) {
                LOGGER.log(Level.INFO, MessageFormat.format("[#{0}] Success response received from CloudAEye endpoint : {1}", buildNumber, EntityUtils.toString(response.getEntity())));
            } else {
                LOGGER.log(Level.SEVERE, MessageFormat.format("[#{0}] Error response received from CloudAEye endpoint : {1}", buildNumber, EntityUtils.toString(response.getEntity())));
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("[#{0}] Error while trying to send run details to CloudAEye : {1}", buildNumber, e.getMessage()));
        }
    }


    /**
     * Extract run logs by converting large annotated text logs to list of strings
     * @param logText The annotated large text
     * @return Json array of log strings
     */
    private JsonArray extractRunLogs(int buildNumber, AnnotatedLargeText<?> logText) throws IOException {
        // Use a StringWriter to collect the log output
        StringWriter writer = new StringWriter();
        logText.writeLogTo(0, writer);
        // Convert the entire log content to a string
        String fullLog = writer.toString();
        String[] lines = fullLog.split("\n");
        // Return the logs as json array of strings
        JsonArray buildLogs = new JsonArray();
        for (String log : lines) {
            buildLogs.add(log);
        }
        LOGGER.info(MessageFormat.format("[#{0}] Total log lines captured : {1}", buildNumber, buildLogs.size()));
        return buildLogs;
    }

    /**
     * Extract the change log sets for the given jenkins run
     * @param run The run to extract change log details
     * @return JSONArray of the change logs
     */
    private JsonArray extractChangeLogsForRun(int buildNumber, Run<?, ?> run) {
        LOGGER.info(MessageFormat.format("[#{0}] Collecting change log set for run : {1}", buildNumber, run.getNumber()));
        List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeLogSets;
        if (run instanceof WorkflowRun) {
            changeLogSets = ((WorkflowRun) run ).getChangeSets();
        } else {
            changeLogSets = ((AbstractBuild<?, ?> )run).getChangeSets();
        }
        /*
            Collect file changes
         */
        JsonArray changeLogs = new JsonArray();
        for (ChangeLogSet<? extends ChangeLogSet.Entry> changeLogSet : changeLogSets) {
            for (ChangeLogSet.Entry entry : changeLogSet) {
                JsonObject changeLog = new JsonObject();
                // Collect change log messages
                changeLog.addProperty("message", entry.getMsg());
                changeLog.addProperty("commitId", entry.getCommitId());
                changeLog.addProperty("author", entry.getAuthor().getId());
                changeLog.addProperty("timestamp", entry.getTimestamp());
                // Collect file path of changed files
                JsonArray filePaths = new JsonArray();
                for (String filePath : entry.getAffectedPaths()) {
                    filePaths.add(filePath);
                }
                changeLog.add("filePaths", filePaths);
                changeLogs.add(changeLog);
            }
        }
        return changeLogs;
    }



    /**
     * Describes the step display name (to show on the jenkins UI)
     */
    @Extension
    @Symbol("sendNotificationsToCloudAEye")
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public DescriptorImpl() {
            super(CloudAEyeNotifications.class);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Send build notifications to CloudAEye";
        }
    }
}
