package org.whispersystems.textsecuregcm.metrics;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.collectd.CollectdReporter;
import com.codahale.metrics.collectd.Sender;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.collect.ImmutableSet;
import io.dropwizard.jersey.DropwizardResourceConfig;
import io.dropwizard.jetty.HttpsConnectorFactory;
import io.dropwizard.metrics.BaseReporterFactory;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Uses <a href="https://metrics.dropwizard.io/4.1.2/manual/collectd.html">the metrics-collectd
 * module</a> to stream metrics to a <a href="https://collectd.org/">Collectd</a> server.
 *
 * <p>If you are running on AWS, then enabling this metrics factory will allow you to send
 * dropwizard metrics to the local CloudWatch Agent. For more information review <a
 * href="https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-Agent-custom-metrics-collectd.html">CloudWatch:
 * Retrieve Custom Metrics with collectd</a>
 */
@JsonTypeName("collectd")
public class CollectdMetricsReporterFactory extends BaseReporterFactory {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(CollectdMetricsReporterFactory.class);

  private static final Set<String> BLACKLIST_METRIC_NAMES =
      ImmutableSet.of(
          // CollectdReport will output ...
          // > Unsupported gauge of type: java.lang.String
          "jvm.attribute.name",
          "jvm.attribute.vendor",
          // CollectdReport will output ...
          // > Unsupported gauge of type: java.util.Collections$EmptySet
          "jvm.threads.deadlocks");

  @JsonProperty @NotNull private String hostname;

  @JsonProperty @NotNull private Integer port;

  private boolean initialized;

  public void setHostname(String hostname) {
    this.hostname = hostname;
  }

  public void setPort(Integer port) {
    this.port = port;
  }

  @Override
  public ScheduledReporter build(MetricRegistry registry) {
    final boolean wasInitialized;
    synchronized (this) {
      wasInitialized = initialized;
      initialized = true;
    }
    if (!wasInitialized) {
      LOGGER.info(String.format("Enabled collectd metrics: udp://%s:%s", this.hostname, this.port));
    }

    // Give back a collectd reporter
    Sender sender = new Sender(this.hostname, this.port);
    return CollectdReporter.forRegistry(registry)
        .convertRatesTo(TimeUnit.SECONDS)
        .convertDurationsTo(TimeUnit.MILLISECONDS)
        .filter((name, metric) -> !BLACKLIST_METRIC_NAMES.contains(name))
        .build(sender);
  }

  private void logSupportedParameters() {
    LOGGER.info(String.format("Enabled collectd metrics: udp://%s:%s", this.hostname, this.port));
  }
}
