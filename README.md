# CloudAEye Jenkins Plugin

Provides integration to send build level notifications to [CloudAEye](https://www.cloudaeye.com/) to perform RCA(Root Cause Analysis) over test failures.

## Install the Plugin

To install this plugin on your Jenkins server, follow the below steps:

 1.  On Jenkins dashboard click on `Manage Jenkins` on left menu.
 2.  Under **System Configuration** select `Plugins`.
 3.  Click on `Available Plugins` option in left menu.
 4.  Search for `CloudAEye` and click the `Install` button next to the search box.

## Configure the Plugin

### Step 1: Fetch plugin credentials from CloudAEye :

1. Login to your CloudAEye account (If you do not already have an account, you can create one by signing up [here](https://console.cloudaeye.com/signup) 
   > CloudAEye offers a **free tier** for individual developers. You can read more about the free-tier [here](https://docs.cloudaeye.com/free-tier.html)
2. Navigate to `Home > Test RCA > Setup`.
3. Select `Connect Jenkins` from the list of available integrations.
4. Copy the `Tenant ID` and `Token` values from the step-by-step guide.

![image][img-cloudaeye-setup]
![image][img-cloudaeye-creds]

### Step 2: Configure the plugin globally in Jenkins

1. On Jenkins dashboard click on `Manage Jenkins` on left menu.
2. Under **System Configuration** select `System`.
3. Search for the `CloudAEye Configuration` section
4. Fill in the `Tenant ID` and `Token` values copied from above steps.
5. Click `Test Connection`. 
   > This would ping the CloudAEye's webhook endpoint to test the connection. A success message indicates the plugin is configured successfully. 

![image][img-global-configuration]


## Enable Plugin for Jenkins Jobs

### For Free-style Jobs:

1. On Dashboard, select the required free-style project.
2. On the left menu select **Configure > Post Build Actions**. 
3. Search for the name `Send build notifications to CloudAEye`.
4. Click check box to `Enable sending build notifications to CloudAEye`
5. Save your changes

![image][img-add-as-postbuild]
![image][img-enable-postbuild]

### For Pipeline Jobs:

1. On Dashboard, select the required pipeline project.
2. On left menu select **Pipeline Syntax > Snippet Generator**
3. In the **Sample Step** drop down, select the option `sendNotificationsToCloudAEye: Send build notifications to CloudAEye`
4. Click check box to `Enable sending build notifications to CloudAEye`
5. Click on `Generate Pipeline Script`. 
6. Open the `Jenkinsfile` script file in the corresponding github project repo and copy the snippet generated in step 5 and add it in the `post section` of the script
   ``` 
   post {
     always {
        sendNotificationsToCloudAEye true
     }
   }
   ```
7. Commit and deploy the changes

![image][img-pipeline-script-generator]

---

## Developer instructions

### Setting Up:

- Clone this repository on to your local machine
```shell
git clone https://github.com/jenkinsci/cloudaeye-plugin
```

- Install Maven and JDK.

```shell
$ mvn -version | grep -v home
Apache Maven 3.3.9 (bb52d8502b132ec0a5a3f4c09453c07478323dc5; 2015-11-10T08:41:47-08:00)
Java version: 1.7.0_79, vendor: Oracle Corporation
Default locale: en_US, platform encoding: UTF-8
OS name: "linux", version: "4.4.0-65-generic", arch: "amd64", family: "unix"
```

### Deployment:

After making changes to the plugin repo, follow the below steps to successfully deploy the changes to Jenkins

- Always work on a feature branch (DO NOT work and push changes directly from main branch).

- Run the below command to locally build and test your plugin.
```shell
mvn clean package
```
> This creates a HPI file that can be installed on your Jenkins server (HPI file will be in `target/cloudaeye.hpi`

- Fix lint issues (if any)

```shell
mvn spotless:apply
```

> This is important because Jenkins build fails if there are any lint issues

- Push the changes and create a PR to the `main` branch. 
  
  NOTE: Make sure to apply [appropriate labels](https://github.com/jenkinsci/.github/blob/master/.github/release-drafter.yml) to the PR that falls into the [interesting category actions](https://github.com/jenkins-infra/interesting-category-action/blob/main/action.yaml#L13) to automatically trigger a release

- Once all `github checks pass`, merge the PR to deploy the changes to Jenkins

[img-global-configuration]: /docs/GlobalConfiguration.png
[img-cloudaeye-setup]: /docs/CloudAEyeSetup.png
[img-add-as-postbuild]: /docs/AddAsPostBuild.png
[img-enable-postbuild]: /docs/EnablePostBuildAction.png
[img-cloudaeye-creds]: /docs/CloudAEyeCreds.png
[img-pipeline-script-generator]: /docs/PipelineScriptGenerator.png