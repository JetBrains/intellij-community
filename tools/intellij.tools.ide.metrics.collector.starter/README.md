### Collect OpenTelemetry metrics from tests run by Starter

Example of usage - [MetricsCollectionTest](https://github.com/JetBrains/intellij-ide-starter/blob/master/intellij.tools.ide.metrics.collector.starter/testSrc/com/intellij/tools/ide/metrics/collector/starter/MetricsCollectionTest.kt)

#### Adding dependency

```
repositories {
  maven { url = "https://cache-redirector.jetbrains.com/maven-central" }
  maven { url = "https://cache-redirector.jetbrains.com/intellij-dependencies" }

  maven { url = "https://www.jetbrains.com/intellij-repository/releases" }
  maven { url = "https://www.jetbrains.com/intellij-repository/snapshots" }
  maven { url = "https://www.jetbrains.com/intellij-repository/nightly" }
}

testImplementation("com.jetbrains.intellij.tools:ide-metrics-collector-starter:LATEST-EAP-SNAPSHOT")
testImplementation("com.jetbrains.fus.reporting:ap-validation:76")
testImplementation("com.jetbrains.fus.reporting:model:76")
```
