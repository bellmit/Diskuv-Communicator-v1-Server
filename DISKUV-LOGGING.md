# Logging

The preferred logging API in this codebase is [slf4j](http://www.slf4j.org/manual.html).
That was a decision by dropwizard and Signal.

There are [adapters that redirect](http://www.slf4j.org/legacy.html) the following log frameworks into slf4j:
* Jakarta Commons Logging
* Java Util Logging
* Log4j 1.x (but not Log4j 2)

The slf4j binds to the [logback-classic](http://logback.qos.ch/reasonsToSwitch.html) logging implementation.

Dropwizard almost immediately on startup configures logback-classic:
* Before the dropwizard configuration `WhisperServicerConfiguration` is instantiated,
  `BootstrapLogging` is used to print logs
* After this, logs are configured using `WhisperServicerConfiguration.getLoggingFactory()`.
  That will be dropwizard's `DefaultLoggingFactory`, corresponding to the following in
  the dropwizard YAML configuration which you are very unlikely to want to change:
```yaml
logging:
  type: default
```  
* You can modify the factory (even though there is no reason to) and [configure the logging using YAML](https://www.dropwizard.io/en/latest/manual/configuration.html#logging)
* Dropwizard uses [logback-access](http://logback.qos.ch/access.html) by
[default for the request logs](https://www.dropwizard.io/en/latest/manual/configuration.html#request-log).
  Without configuration the [pattern layout](http://logback.qos.ch/manual/layouts.html#AccessPatternLayout) is given by `LogbackAccessRequestLayout`

That leaves Log4j 2 ... anything that uses the Logj4 2 API will go into the nether
since Log4j 2 is not configured. So don't use the Log4j 2 API.
