# Developing the Diskuv Communicator Server

## Setup

FIRST, install the following if you do not have them:

* Install [Maven](https://maven.apache.org/install.html) after [downloading it](https://maven.apache.org/download.cgi).
  It has been tested with Maven 3.6.3
* Install JDK 11. It has been tested with [Amazon Coretto](https://aws.amazon.com/corretto/)
* Install [Redis](https://redis.io/topics/quickstart), or have a Redis server accessible
  from your local network (which can include locally-connected cloud instances). You will need
  both a standalone Redis instance and a Redis Cluster.
* Install [PostgreSQL 9.4](https://www.postgresql.org/download/), or have a PostgreSQL server accessible
  from your local network (which can include locally-connected cloud instances). *Optional*: Versions later than 9.4
  will likely work, but it is safest to use the PostgreSQL version
  that matches the JDBC driver in `service/pom.xml`. *Advanced*:
  [OpenTable Embedded PostgreSQL](https://github.com/opentable/otj-pg-embedded)
  is used for unit tests; you may be able to adapt it so you don't
  need a full installation of PostgreSQL for local development.
* Install [DynamoDB Local](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBLocal.DownloadingAndRunning.html),
  or have an AWS account available.

THEN, before running any Maven (`mvn`) commands, make sure your JAVA_HOME environment variable is set to the JDK 11
installation. For example, in Windows PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Amazon Corretto\jdk11.0.8_10\'
```

or in Linux or macOS:

```bash
set JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk11.0.8_10.jdk/Contents/Home
```

FINALLY, to verify your installation, try:

```bash
mvn -v
```

which should look something like:

```text
Apache Maven 3.6.3 (cecedd343002696d0abb50b32b541b8a6ba2883f)
Maven home: C:\Program Files\apache-maven-3.6.3\bin\..
Java version: 11.0.8, vendor: Amazon.com Inc., runtime: C:\Program Files\Amazon Corretto\jdk11.0.8_10
Default locale: en_US, platform encoding: Cp1252
OS name: "windows 10", version: "10.0", arch: "amd64", family: "windows"
```

## Building

```bash
mvn package
```

On Windows, you must skip the unit tests with the following until the unit tests can be fixed:

```bash
mvn package -DskipTests
```

## Configuring

### YAML File

You will need to generate a YAML file. Use:

```bash
java -jar configurator/target/configurator-*.jar
```

to run the `generate` command. Consult with its `--help` option.

The YAML file should created as `local.yml`, or any `service/config/xxx.yml` file listed in the `.gitignore` file.

### Local Testing

You will need to expose your
local server to the Internet so that Twilio can call back your local server.

The best options are [localtunnel](https://github.com/localtunnel/localtunnel#readme)
if you have node.js installed on your machine:

```bash
npx localtunnel --port 8010
```

or <https://ngrok.com/> which is similar but requires you to sign-up (for free).
If you do this often, you will want the more permanent solution provided by
[go-http-tunnel](https://github.com/mmatczuk/go-http-tunnel#readme)

---

Here is a list of changes you will have to make to the source code in
Diskuv-Communicator-Android (or Signal-Android):
* `app/build.gradle` > android > productFlavors > staging > SIGNAL_URL
* Follow the "Regenerating the trust store" section of Diskuv-Communicator-Android's
  `DISKUV-DEVELOPING.md`. You will need to modify its `$localtunnels` variable before
  executing the code snippet.

The changes you will need to this project (Diskuv-Communicator-Server) are:
* `local.yml` > twilio > localDomain

### Configuring Third-Party Vendors and Components

#### OAuth2 Identity Provider: Auth0 or AWS Cognito

Auth0 or AWS Cognito authenticates the Android/iOS/web users and provide an authenticated user with a
JWT token containing signed claims. You have to configure the JWT public keys (something called a
JSON Web Key Set) so that JWT signed claims from the users (Android/iOS/web) can be validated.

AWS Cognito has been extensively tested, but Auth0 should work as well. In fact, some Auth0 libraries
are used in the server.

In the YAML file you will have:

```yaml
# JSON Web Key Set.
# Needed to validate the OAuth2 (authentication) claims from the Android/iOS clients.
# You need to onboard with an identity provider like Auth0, AWS Cognito, etc.
jwtKeys:
  # The JSON Web Key Set can be downloaded from:
  # * Auth0: https://YOUR_DOMAIN/.well-known/jwks.json ; see https://auth0.com/docs/tokens/json-web-tokens/json-web-key-sets
  # * Cognito: https://cognito-idp.{region}.amazonaws.com/{userPoolId}/.well-known/jwks.json ; see https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-tokens-verifying-a-jwt.html
  # The 'domain' is the URL before the '/.well-known/jwks.json' ending
  domain: https://cognito-idp.us-west-2.amazonaws.com/us-west-2_XXXXXXXXX
  # The Android and iOS client ids for the Cognito User Pool.
  # Use *none* if not Cognito. And do not specify any
  # client ids that do not call this server.
  appClientIds:
    - fffffffffffffffffffffffff
```

Once you have edited your YAML file, run:

```bash
java -jar configurator/target/configurator-*.jar generate-code-config local.yml service/src/main/resources/jwtKeys/
```

to download the key set.

Add the new public key files in `service/src/main/resources/jwtKeys/` to source control.

---

It is possible that the key sets will change. Periodically you run:

```bash
java -jar configurator/target/configurator-*.jar validate-code-config
```

to check that the key sets in source control are the latest from your OAuth2 service
provider.

You can do this every code commit, but it is better doing this check every day.
If you use GitHub as your repository, the scheduled action
at `.github/workflows/validate-code-config.yml` will automatically check on your
behalf every day.

#### AWS AppConfig

Diskuv doesn't use this, but the official Signal server does.

We suggest you disable this with the following in your YAML config:

```yaml
appConfig:
  skipAppConfig: true
  application: DiskuvCommunicator
  environment: GenericEnvironment
  configuration: GenericConfig
```

#### Redis

You are required to have a Redis non-clustered server with at least one replica. You are *also* required to have a Redis Cluster.

You also need to have [notifications enabled](https://redis.io/topics/notifications) in both your Redis non-clustered
server and Redis Cluster. Notifications are typically disabled by default, but are necessary to deliver messages
quickly ("ephemerally", which for Signal Server is under first 10 seconds). Each letter of the `notify-keyspace-events` config option controls notifications; the
value `KA` should work for Signal Server:
* `K` - Keyspace events, published with `__keyspace@<db>__` prefix.
* `A` - Alias for "g$lshztxed", so that the "AKE" string means all the events except "m".

If you are using AWS ElastiCache, you **must** follow
[How do I implement Redis keyspace notifications in ElastiCache?](https://aws.amazon.com/premiumsupport/knowledge-center/elasticache-redis-keyspace-notifications/)
to enable the notifications.

For clarity, this section documents local Ubuntu instructions. Cloud Redis servers need to be provisioned using
their web consoles or CLIs.

---

> The following will start up a Redis server with one replica

In one terminal, run the following to start a Redis server:

```bash
install -d /tmp/redis-server/primary
redis-server --dir /tmp/redis-server/primary --maxclients 100 --port 6379 --notify-keyspace-events KA
```

In another terminal, run:

```bash
install -d /tmp/redis-server/replica
redis-server --dir /tmp/redis-server/replica --maxclients 100 --port 7777 --notify-keyspace-events KA --replicaof 127.0.0.1 6379
```

---

> The following will start up a Redis Cluster with 3 master nodes (shards), each having no replicas

In your first "Cluster" terminal, run:

```bash
install -d /tmp/redis-server/cluster/7000
redis-server --dir /tmp/redis-server/cluster/7000 --port 7000 --notify-keyspace-events KA --cluster-enabled yes --cluster-config-file nodes.conf --cluster-node-timeout 5000 --appendonly yes
```

In your second "Cluster" terminal, run:

```bash
install -d /tmp/redis-server/cluster/7001
redis-server --dir /tmp/redis-server/cluster/7001 --port 7001 --notify-keyspace-events KA --cluster-enabled yes --cluster-config-file nodes.conf --cluster-node-timeout 5000 --appendonly yes
```

In your third "Cluster" terminal, run:

```bash
install -d /tmp/redis-server/cluster/7002
redis-server --dir /tmp/redis-server/cluster/7002 --port 7002 --notify-keyspace-events KA --cluster-enabled yes --cluster-config-file nodes.conf --cluster-node-timeout 5000 --appendonly yes
```

In your last (fourth) "Cluster" terminal, run:

```bash
redis-cli --cluster create --cluster-yes 127.0.0.1:7000 127.0.0.1:7001 127.0.0.1:7002 --cluster-replicas 0
```

---

After the Redis Cluster and Redis standalone servers are started, use the following YAML configuration:

```yaml
cacheCluster:
    urls:
        - redis://localhost:7000?database=1
        - redis://localhost:7001?database=1
        - redis://localhost:7002?database=1

pubsub:
    url: redis://localhost:6379#2
    replicaUrls:
        - redis://localhost:7777#2

metricsCluster:
    urls:
        - redis://localhost:7000?database=3
        - redis://localhost:7001?database=3
        - redis://localhost:7002?database=3

pushSchedulerCluster:
    urls:
        - redis://localhost:7000?database=4
        - redis://localhost:7001?database=4
        - redis://localhost:7002?database=4

rateLimitersCluster:
    urls:
        - redis://localhost:7000?database=5
        - redis://localhost:7001?database=5
        - redis://localhost:7002?database=5

messageCache:
    persistDelayMinutes: 10
    cluster:
        urls:
            # messages are hardcoded to database 0 through Redis pubsub __keyspace@0__
            - redis://localhost:7000?database=0
            - redis://localhost:7001?database=0
            - redis://localhost:7002?database=0

clientPresenceCluster:
    urls:
        # client presence is hardcoded to database 0 through Redis pubsub __keyspace@0__
        - redis://localhost:7000?database=0
        - redis://localhost:7001?database=0
        - redis://localhost:7002?database=0
```

#### DynamoDB

You'll need to create two tables.

FIRST, if you are using DynamoDB Local (recommended if you are getting started), then in a separate terminal run:

```bash
install -d /tmp/ddblocal
# Change ~/opt/dynamodb-local to wherever you installed the program
java -jar ~/opt/dynamodb-local/DynamoDBLocal.jar -port 8881 -sharedDb -dbPath /tmp/ddblocal -delayTransientStatuses
```

... and then change the YAML configuration to include the following:

```yaml
diskuvGroups:
  region: us-west-2 # Ignored, but must be specified
  credentialsType: BASIC
  endpointOverride: http://localhost:8881
  accessKey: AKIAIOSFODNN7EXAMPLE # Ignored, but must be specified
  secretKey: wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY # Ignored, but must be specified
```

If you are using a real AWS account, then change the YAML configuration to include either:

```yaml
# Use this style of configuration when you are running in AWS (ex. EC2, ECS) or have set AWS_ environment variables
diskuvGroups:
  region: us-west-2
  credentialsType: DEFAULT
```

or

```yaml
# Use this style of configuration when you have an IAM user
diskuvGroups:
  region: us-west-2
  credentialsType: BASIC
  accessKey: AKIAIOSFODNN7EXAMPLE
  secretKey: wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
```

SECOND, regardless of whether you are using DynamoDB Local, run the following:

```bash
java -jar service/target/TextSecureServer-*.jar creategroupstable   local.yml
java -jar service/target/TextSecureServer-*.jar creategrouplogtable local.yml
java -jar service/target/TextSecureServer-*.jar createsanctuariestable   local.yml
```

#### PostgreSQL

You'll need to create two databases and a user for each database.
For cloud databases
like AWS Aurora, you will need to log into the web console and use their database creation wizards.
For clarity, the instructions here are for a local Ubuntu database, which requires logging into the
database as so:

```bash
sudo -u postgres psql
```

and then executing the following SQL commands:

```sql
CREATE USER signal_abuse_user WITH PASSWORD 'pick a password';
CREATE USER signal_account_user WITH PASSWORD 'pick another password';

-- Give yourself permissions to use these new users (mandatory for PostgreSQL 11; not mandatory PostgreSQL 9; other versions untested)
GRANT signal_abuse_user TO current_user;
GRANT signal_account_user TO current_user;

CREATE DATABASE signal_abuse WITH OWNER signal_abuse_user ENCODING 'UTF8' LC_COLLATE 'en_US.UTF-8' LC_CTYPE 'en_US.UTF-8' TEMPLATE 'template0';
CREATE DATABASE signal_account WITH OWNER signal_account_user ENCODING 'UTF8' LC_COLLATE 'en_US.UTF-8' LC_CTYPE 'en_US.UTF-8' TEMPLATE 'template0';
```

After the databases are created, change the YAML configuration to look like:

```yaml
abuseDatabase:
  driverClass: org.postgresql.Driver
  user: signal_abuse_user
  password: 'your chosen password'
  url: jdbc:postgresql://localhost:5432/signal_abuse

accountsDatabase:
  driverClass: org.postgresql.Driver
  user: signal_account_user
  password: 'your chosen password'
  url: jdbc:postgresql://localhost:5432/signal_account
```

Change the URLs if you are using a cloud or network database, and change the passwords of course.

#### AWS S3

FIRST,

You will need to create an AWS S3 bucket. If you haven't already selected a region to place your AWS
resources, the recommendation is to choose the `us-east-1` **US East (N. Virginia) Region**, since
[AWS Credentials Manager](https://docs.aws.amazon.com/acm/latest/userguide/acm-regions.html) is restricted to
that region when using CloudFront and because
[AWS CloudFront Triggers](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/lambda-requirements-limits.html)
must be in that region.

SECOND,

Create an IAM user at <https://console.aws.amazon.com/iam/home?#/users$new?step=details> :

* User name: `signal-server-s3` (anything)
* Access type: `Programmatic access`
* Set permissions > Attach existing policies directly > Create Policy:

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "s3:PutObject",
                "s3:DeleteObject"
            ],
            "Resource": "arn:aws:s3:::THE-NAME-OF-YOUR-NEWLY-CREATED-BUCKET/*"
        }
    ]
}
```

* Attach that policy to the IAM user, and finish creating the IAM user

You will be given an Access key, and a Secret access key, which you can use in the YAML configuration:

```yaml
cdn:
  accessKey: THE-ACCESS-KEY
  accessSecret: THE-SECRET-ACCESS-KEY
  bucket: THE-NAME-OF-YOUR-NEWLY-CREATED-BUCKET
  region: us-east-1
```

#### AWS SQS

SQS is used to communicate between the Signal Servers and the
[Contact Discovery Service](https://github.com/signalapp/ContactDiscoveryService#readme).
We are not using the Contact Discovery Service, so we've made a change to the
code to disable the sending of messages to SQS.

#### Twilio

##### Just Starting?

1. You can sign up with Twilio for a free trial. You will see an
   `ACCOUNT SID` and a `AUTH TOKEN` on the Twilio dashboard. Those go
   into `twilio.accountId` and `twilio.accountToken`, respectively.
   Note: Twilio gives you free test credentials

2. On <https://www.twilio.com/console>, "Get a Trial Phone Number". This phone number
   will go into `twilio.number`

3. Create a "Messaging Service" at <https://www.twilio.com/console/sms/services>. You
   will be asked to create a Sender Pool; use a Sender Type = `Phone Number` and add
   the phone number you created in the last step.
   Place the "Messaging Service SID" as `twilio.messagingServicesId`.

4. Follow <https://www.twilio.com/docs/usage/tutorials/how-to-use-your-free-trial-account#verify-your-personal-phone-number>
   to give Twilio permission to send SMS to your cell phone (in Trial mode you do not have permission
   to SMS non-approved people)

Theoretically, you may use the
[test credentials](https://www.twilio.com/docs/iam/test-credentials) and
the test phone number `+15005550006` for `twilio.accountId` and `twilio.accountToken`
and `twilio.number`. However, this has not been tested and functionality will be
limited.

You will need a Twilio phone number with a [TwiML](https://www.twilio.com/docs/voice/twiml)
URL. That URL will instruct Twilio what to do within each voice verification call.

## Understanding

Reading the following will help:

* <https://www.dropwizard.io/en/latest/getting-started.html>

## Running

### Locally

Make sure you have started:

* the Redis server and at least one replica
* the PostgreSQL database
* the DynamoDB Local database

Then run:

```bash
# Setup and/or upgrade the database tables
java -jar service/target/TextSecureServer-*.jar accountdb status local.yml
java -jar service/target/TextSecureServer-*.jar accountdb migrate local.yml
java -jar service/target/TextSecureServer-*.jar abusedb status local.yml
java -jar service/target/TextSecureServer-*.jar abusedb migrate local.yml

# Start the TextSecureServer
java -Duser.timezone=GMT -jar service/target/TextSecureServer-*.jar server local.yml
```

### Production

You will need to have at least once initially, and after every database upgrade, run the following
on one server host (your deployment paths will likely be different):

```bash
java -jar ./TextSecureServer-*.jar accountdb status var/conf/config.yml
java -jar ./TextSecureServer-*.jar accountdb migrate var/conf/config.yml
java -jar ./TextSecureServer-*.jar abusedb status var/conf/config.yml
java -jar ./TextSecureServer-*.jar abusedb migrate var/conf/config.yml
```

And as part of your release to production procedures (or an automated pipeline),
validate the configuration embedded in the source code:

```bash
java -jar configurator/target/configurator-*.jar validate-code-config
```
