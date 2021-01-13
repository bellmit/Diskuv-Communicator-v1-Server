# Setup

FIRST, install the following if you do not have them:

* Install [Maven](https://maven.apache.org/install.html) after [downloading it](https://maven.apache.org/download.cgi).
  It has been tested with Maven 3.6.3
* Install JDK 11. It has been tested with [Amazon Coretto](https://aws.amazon.com/corretto/)

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
