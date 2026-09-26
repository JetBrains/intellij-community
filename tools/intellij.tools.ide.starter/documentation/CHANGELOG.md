## 2026.2

* Examples were moved to a separate github repo: [ide-starter-examples](https://github.com/JetBrains/ide-starter-examples)
* Source code was moved to the community code base.
  Most of the modules you are interested in could be found [here]([https://github.com/JetBrains/intellij-community/tree/master/tools](https://github.com/JetBrains/intellij-community/tree/master/tools)).

### Breaking changes
* If you want to use default IdeInfo of a certain IDE, you need to add 
`testImplementation("com.jetbrains.intellij.tools:ide-starter-product-idea-ultimate:LATEST-EAP-SNAPSHOT")` dependency
* To make tests work, please add `jvmArgs("--add-opens=java.base/sun.nio.fs=ALL-UNNAMED")` as done in examples
* IC test cases were dropped as IC was merged with IU in 2025.3 version
* Please update to `jvmToolchain(25)` and `kotlin("jvm") version "2.3.0"` to be able to run recent IDE versions

## 2025.1

### Improvements

* New method `com.intellij.ide.starter.models.VMOptions.configureLoggers` to configure loggers
* ScreenRecorder is disabled for Wayland since it causes system dialog
* `runIde` now accepts `ExecOutputRedirect` as a parameter to redirect output of the IDE process
* New event type `IdeBeforeRunIdeProcessEvent` which can be used to subscribe right before IDE start

### Fixes

* `ExistingIdeInstaller` was fixed on Windows
* Various improvements for Driver and split mode
* Fix Rider downloading 
* Freezes are reported only from errors folder to avoid false positives

### Breaking changes
* `getListOfIndexingMetrics()` now returns `IndexingMetric`. Please use the following code to convert to standard `Metric`.
```kotlin
val indexingMetrics = extractIndexingMetrics(results).getListOfIndexingMetrics().map {
      when (it) {
        is IndexingMetric.Duration -> PerformanceMetrics.newDuration(it.name, it.durationMillis)
        is IndexingMetric.Counter -> PerformanceMetrics.newCounter(it.name, it.value)
      }
    }
```

## 2024.3

### Improvements

* Parsing of 2Gb opentelemetry.json now requires 3x less heap size
* Reworked and unified metrics collection. There is `MetricsCollector` interface with the main implementations
  `StarterTelemetrySpanCollector` and
  `StarterTelemetryJsonMeterCollector` for collection spans and meters respectively.
* It's possible to customize `PublicIdeDownloader` by overriding `mapDownloadLink` method
* `TimeoutAnalyzer` class that infers the reason of hanging test
* Implementation of Split mode which can be run using env variable `REMOTE_DEV_RUN=true`

### Fixes
* Environment variables are not filtered out on Linux

### Breaking changes

* Method `com.intellij.tools.ide.metrics.collector.starter.collector.getMetricsFromSpanAndChildren` was removed.
  Please use `StarterTelemetrySpanCollector(spanFilter).collect(ideStartResult.runContext)` instead.
* In `com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics.Metric` field `value` is now `Int` instead of `Long`.
* `com.intellij.tools.ide.metrics.collector.publishing.IJPerfMetricsDto` was simplified to include only the required data:
  * `buildInfo` is no longer needed for `create` method
  * Fields `productCode`, `tcInfo`, `os`, `osFamily`, `runtime`, `branch` were removed
  * new value `mode` was added to distinguish, for example, `split` and `monolith`
* Kotlin 2.0 is required

  
## 2024.2

### Improvements

* A new protocol to communicate with an IDE is introduced — Driver. See [doc](../../../../tests/ideTestingFramework/intellij.tools.ide.starter.driver/README.md) for more
  details.