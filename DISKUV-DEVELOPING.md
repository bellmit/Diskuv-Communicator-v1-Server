# Setup

FIRST, install the following if you do not have them:

* Install [Maven](https://maven.apache.org/install.html) after [downloading it](https://maven.apache.org/download.cgi).
  It has been tested with Maven 3.6.3
* Install JDK 11. It has been tested with [Amazon Coretto](https://aws.amazon.com/corretto/)
* Install [Redis](https://redis.io/topics/quickstart), or have a Redis server accessible
  from your local network (including cloud instances) 
* Install [PostgreSQL 9.4](https://www.postgresql.org/download/), or have a PostgreSQL server accessible
  from your local network (including cloud instances). *Optional*: Versions later than 9.4
  will likely work, but it is safest to use the PostgreSQL version
  that matches the JDBC driver in `service/pom.xml`. *Advanced*:
  [OpenTable Embedded PostgreSQL](https://github.com/opentable/otj-pg-embedded)
  is used for unit tests; you may be able to adapt it so you don't
  need a full installation of PostgreSQL for local development.

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

```
Apache Maven 3.6.3 (cecedd343002696d0abb50b32b541b8a6ba2883f)
Maven home: C:\Program Files\apache-maven-3.6.3\bin\..
Java version: 11.0.8, vendor: Amazon.com Inc., runtime: C:\Program Files\Amazon Corretto\jdk11.0.8_10
Default locale: en_US, platform encoding: Cp1252
OS name: "windows 10", version: "10.0", arch: "amd64", family: "windows"
```
# Building

```bash
mvn package
```

On Windows, you must skip the unit tests with the following until the unit tests can be fixed:

```bash
mvn package -DskipTests
```

# Configuring

## Local Testing

You will need to expose your
local server to the Internet so that Twilio can call back your local server.

The best options are [localtunnel](https://github.com/localtunnel/localtunnel#readme)
if you have node.js installed on your machine:

```bash
npx localtunnel --port 8010
```

or https://ngrok.com/ which is similar but requires you to sign-up (for free).
If you do this often, you will want the more permanent solution provided by
(go-http-tunnel)[https://github.com/mmatczuk/go-http-tunnel#readme]

## Configuring Third-Party Vendors and Components

### Redis

You are required to have a Redis server and at least one replica. 

For clarity, this section documents local Ubuntu instructions. Cloud Redis servers need to be provisioned using
their web consoles or CLIs.

In one terminal, run the following to start a Redis server:

```bash
redis-server --port 6379
```

In another terminal, run:

```bash
redis-server --port 7777 --replicaof 127.0.0.1 6379
```

After those Redis servers are started, use the following YAML configuration:

```yaml
cache:
  url: localhost:6379
  replicaUrls:
    - localhost:7777

directory:
  redis:
    url: localhost:6379
    replicaUrls:
      - localhost:7777

messageCache:
  redis:
    url: localhost:6379
    replicaUrls:
      - localhost:7777
```

### PostgreSQL

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
CREATE USER signal_message_user WITH PASSWORD 'pick yet another password';

CREATE DATABASE signal_abuse WITH OWNER signal_abuse_user ENCODING 'UTF8' LC_COLLATE 'en_US.UTF-8' LC_CTYPE 'en_US.UTF-8' TEMPLATE 'template0';
CREATE DATABASE signal_account WITH OWNER signal_account_user ENCODING 'UTF8' LC_COLLATE 'en_US.UTF-8' LC_CTYPE 'en_US.UTF-8' TEMPLATE 'template0';
CREATE DATABASE signal_message WITH OWNER signal_message_user ENCODING 'UTF8' LC_COLLATE 'en_US.UTF-8' LC_CTYPE 'en_US.UTF-8' TEMPLATE 'template0';
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

messageStore:
  driverClass: org.postgresql.Driver
  user: signal_message_user
  password: 'your chosen password'
  url: jdbc:postgresql://localhost:5432/signal_message
```

Change the URLs if you are using a cloud or network database, and change the passwords of course.

### AWS S3

FIRST,

You will need to create an AWS S3 bucket. If you haven't already selected a region to place your AWS
resources, the recommendation is to choose the `us-east-1` **US East (N. Virginia) Region**, since
[AWS Credentials Manager](https://docs.aws.amazon.com/acm/latest/userguide/acm-regions.html) is restricted to
that region when using CloudFront and because
[AWS CloudFront Triggers](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/lambda-requirements-limits.html)
must be in that region.

SECOND,

Create an IAM user at https://console.aws.amazon.com/iam/home?#/users$new?step=details :

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

### AWS SQS

SQS is used to communicate between the Signal Servers and the
[Contact Discovery Service](https://github.com/signalapp/ContactDiscoveryService#readme).
We are not using the Contact Discovery Service, so we've made a change to the
code to disable the sending of messages to SQS.

### Twilio

#### Just Starting?

1. You can sign up with Twilio for a free trial. You will see an
   `ACCOUNT SID` and a `AUTH TOKEN` on the Twilio dashboard. Those go
   into `twilio.accountId` and `twilio.accountToken`, respectively.
   Note: Twilio gives you free test credentials
   
2. On https://www.twilio.com/console, "Get a Trial Phone Number". This phone number
   will go into `twilio.number`
   
3. Create a "Messaging Service" at https://www.twilio.com/console/sms/services. You
   will be asked to create a Sender Pool; use a Sender Type = `Phone Number` and add
   the phone number you created in the last step.
   Place the "Messaging Service SID" as `twilio.messagingServicesId`.

Theoretically, you may use the
[test credentials](https://www.twilio.com/docs/iam/test-credentials) and
the test phone number `+15005550006` for `twilio.accountId` and `twilio.accountToken`
and `twilio.number`. However, this has not been tested and functionality will be
limited.

You will need a Twilio phone number with a [TwiML](https://www.twilio.com/docs/voice/twiml)
URL. That URL will instruct Twilio what to do within each voice verification call.  


# Understanding

Reading the following will help:
* https://www.dropwizard.io/en/latest/getting-started.html

# Running

## Locally

Make sure you have started:
* the Redis server and at least one replica
* the PostgreSQL database

Then run:

```bash
# Setup and/or upgrade the database tables
java -jar service/target/TextSecureServer-*.jar accountdb status local.yml
java -jar service/target/TextSecureServer-*.jar accountdb migrate local.yml
java -jar service/target/TextSecureServer-*.jar messagedb status local.yml
java -jar service/target/TextSecureServer-*.jar messagedb migrate local.yml
java -jar service/target/TextSecureServer-*.jar abusedb status local.yml
java -jar service/target/TextSecureServer-*.jar abusedb migrate local.yml

# Start the TextSecureServer
java -jar service/target/TextSecureServer-*.jar server local.yml
```
