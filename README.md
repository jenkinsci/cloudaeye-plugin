# CloudAEye plugin for Jenkins

Provides integration to send build level notifications to [CloudAEye](https://www.cloudaeye.com/) to perform RCA(Root Cause Analysis) over test failures.

## Install Instructions for CloudAEye Plugin

1. Install this plugin on your Jenkins server:

    1.  From the Jenkins homepage navigate to `Manage Jenkins`
    2.  Navigate to `Manage Plugins`,
    3.  Change the tab to `Available`,
    4.  Search for `CloudAEye`,
    5.  Check the box next to install.

### Configuring the Plugin

*On CloudAEye :*
1. Create an account: CloudAEye offers a **free tier** for individual developers. You may get started by signing up [here](https://console.cloudaeye.com/signup). You can read more about the free-tier [here](https://docs.cloudaeye.com/free-tier.html)
2. Navigate to Home > Test RCA > Setup.
3. Select `Jenkins` from the list of available integrations.
4. Copy the `Tenant ID` and `Token` values from the step-by-step guide.

![image][img-cloudaeye-setup]
![image][img-cloudaeye-creds]

*On Jenkins :*
1. Goto "Manage Jenkins → System". Search for the CloudAEye configuration section
2. Fill in the `Tenant ID` and `Token` values copied from above steps.
3. Click `Test Connection`. This would make a ping to the CloudAEye's webhook endpoint to test the connection.

![image][img-global-configuration]


### Enable plugin for Free-style jobs

1. From Dashboard, select the required free-style project.
2. Goto Configure > Post Build Actions. Search for the name `Send build notifications to CloudAEye`.
3. Click check box to `Enable sending build notifications to CloudAEye`
4. Save your changes

![image][img-add-as-postbuild]
![image][img-enable-postbuild]

### Enable plugin for pipeline jobs

1. From Dashboard, select the required pipeline project.
2. Goto Pipeline Syntax > Snippet Generator
3. In Sample Step drop down, select the option `sendNotificationsToCloudAEye: Send build notifications to CloudAEye`
4. Check the option to enable sending build notifications
5. Click `Generate Pipeline Script`. 
6. Open the `Jenkinsfile` script file in the select project repo. 
7. Copy the snippet generated in step 5 and add it in the post section of the script
   ``` 
   post {
     always {
        sendNotificationsToCloudAEye true
     }
   }
   ```

## Developer instructions

Install Maven and JDK.

```shell
$ mvn -version | grep -v home
Apache Maven 3.3.9 (bb52d8502b132ec0a5a3f4c09453c07478323dc5; 2015-11-10T08:41:47-08:00)
Java version: 1.7.0_79, vendor: Oracle Corporation
Default locale: en_US, platform encoding: UTF-8
OS name: "linux", version: "4.4.0-65-generic", arch: "amd64", family: "unix"
```

Create an HPI file to install in Jenkins (HPI file will be in
`target/cloudaeye.hpi`).

```shell
mvn clean package
```

[img-global-configuration]: /docs/GlobalConfiguration.png
[img-cloudaeye-setup]: /docs/CloudAEyeSetup.png
[img-add-as-postbuild]: /docs/AddAsPostBuild.png
[img-enable-postbuild]: /docs/EnablePostBuildAction.png
[img-cloudaeye-creds]: /docs/CloudAEyeCreds.png