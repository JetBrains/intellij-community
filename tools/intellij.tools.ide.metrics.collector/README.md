### Metrics Collector
This modules provides OpenTelemetry metrics collection.

There are 2 types of OpenTelemetry metrics that can be collected: [spans](https://opentelemetry.io/docs/concepts/signals/traces/#spans) 
and [meters](https://opentelemetry.io/docs/specs/otel/metrics/api/#meter).  
Spans are exported to `opentelemetry.json` file (usually located in log directory) by registered span exporters in `com.intellij.platform.diagnostic.telemetry.TelemetryManager`.  
To enable span exports provide system property `idea.diagnostic.opentelemetry.file` (basically it should be ide_log_directory/opentelemetry.json)

Meters will be exported to `*.json` files in log directory by meter exporters (also via `TelemetryManager`).

- Spans:  
To get reported spans use `com.intellij.tools.ide.metrics.collector.OpenTelemetrySpanCollector` and pass the span name you're interested in.  
Default span name for all commands from performance test is `performance_test`.  
All the child spans under your provided span will be collected too.  

To visualize spans you can use [Jaeger](https://www.jaegertracing.io/).

- Meters:
To get reported meters use `com.intellij.tools.ide.metrics.collector.OpenTelemetryJsonMeterCollector`.  
You can provide filter for metrics to narrow down scope of collected metrics.  
Also, you should provide metrics selection strategy (SUM, LATEST, etc.), since OpenTelemetry reports, for example, counters and gauges in different manner.

#### Metrics publishing

You may create/use metrics publisher similar to Starter - `com.intellij.tools.ide.metrics.collector.starter.publishing.MetricsPublisher`.  
 
#### Adding dependency

```
repositories {
  maven { url = "https://cache-redirector.jetbrains.com/maven-central" }
  maven { url = "https://cache-redirector.jetbrains.com/intellij-dependencies" }

  maven { url = "https://www.jetbrains.com/intellij-repository/releases" }
  maven { url = "https://www.jetbrains.com/intellij-repository/snapshots" }
  maven { url = "https://www.jetbrains.com/intellij-repository/nightly" }
}

testImplementation("com.jetbrains.intellij.tools:ide-metrics-collector:LATEST-EAP-SNAPSHOT")
```
