### Metric Collector

#### Overview

Extension library for [starter](https://github.com/JetBrains/intellij-ide-starter/tree/master/intellij.tools.ide.starter)
that provides metrics collection.

Supported metrics:
* Indexing
* Opentelemetry-based metrics

To get the metrics from OpenTelemetry invoke `com.intellij.metricsCollector.metrics.OpenTelemetryKt.getOpenTelemetry`
and pass the span name you're interested in. Default span name for all commands from performance test is `performance_test`.

To find out which spans were collected you can open `opentelemetry.json` file in log folder. The recommended viewer is [Jaeger](https://www.jaegertracing.io/).

To get inspection metrics invoke `com.intellij.metricsCollector.metrics.IndexingMetricsKt.extractIndexingMetrics`.
