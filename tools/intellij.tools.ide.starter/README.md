### Starter Core

#### Overview

The core of the Starter test framework for IntelliJ IDEA-based IDEs. For a general overview, refer to
the [main README](https://github.com/JetBrains/intellij-ide-starter/blob/master/README.md)

##### Basics

Test starts IDE in a separate process so the test runtime and the IDE runtime are isolated.
To control IDE you should use commands.
There are two ways to make the IDE execute the command

1) Write a scenario to the file (list of plain text strings in a special format) and pass it to the IDE
2) trigger single command remotely with a JMX call (Driver implementation).  
   Currently is not available in a public version of Starter.

In both cases commands will be executed
via [performanceTestingPlugin](https://github.com/JetBrains/intellij-community/tree/1bf43101d9e285b23906c9952ebc37077a9e9dc9/plugins/performanceTesting)
or it's extension points.

List of basic out-of-the-box commands that goes with performanceTestingPlugin is
available [here](https://github.com/JetBrains/intellij-community/blob/9f011b378a6fffe4859f76966d70a63910e3e1c8/plugins/performanceTesting/commands-model/src/com/intellij/tools/ide/performanceTesting/commands/generalCommandChain.kt)

##### Run with JUnit5

Starter isn't bound to any of test engines, so you can run it via any test engine you like.  
But there is ready to use JUnit5 integration library
and [examples of tests based on JUnit5 can be found here](https://github.com/JetBrains/intellij-ide-starter/blob/master/intellij.tools.ide.starter.examples/testSrc/com/intellij/ide/starter/examples/junit5/IdeaJUnit5ExampleTest.kt)

##### Short guide how to write your own command/extension of performanceTestingPlugin

See [createCustomPerformanceCommand.md](documentation/createCustomPerformanceCommand.md)

#### How to override/modify default starter behavior

You can modify or extend any behavior initialized through the Kodein DI framework according to your needs. To do so, refer to the    
[DI container initialization](https://github.com/JetBrains/intellij-ide-starter/blob/master/intellij.tools.ide.starter/src/com/intellij/ide/starter/di/diContainer.kt)  
For example, you can create your own implementation of CIServer and provide it through DI. Make sure to use the same Kodein version
specified in the starter project's `build.gradle`.

Example:

```
di = DI {
      extend(di)
      bindSingleton<CIServer>(overrides = true) { YourImplementationOfCI() }
}
```

### Freeze/exception collection

Freezes or exceptions are collected by default by Starter and reported as an individual failure of a test on CI.  
To enable this machinery you should
provide [an implementation of your CiServer](https://github.com/JetBrains/intellij-ide-starter/blob/8c19f61989510def61e864515014d6e0df358342/intellij.tools.ide.starter/src/com/intellij/ide/starter/di/diContainer.kt#L50) (
by
default [NoCiServer](https://github.com/JetBrains/intellij-ide-starter/blob/8c19f61989510def61e864515014d6e0df358342/intellij.tools.ide.starter/src/com/intellij/ide/starter/ci/NoCIServer.kt#L7)
is used).  
As an example of implementation you may take a look
at [TeamCityCiServer](https://github.com/JetBrains/intellij-ide-starter/blob/8c19f61989510def61e864515014d6e0df358342/intellij.tools.ide.starter/src/com/intellij/ide/starter/ci/teamcity/TeamCityCIServer.kt#L18).
Yust override your CiServer in DI (as described in the code snipped above) and reporting of freezes and exceptions should work.

If you want a more detailed customization you might find the following useful:
Test reports errors
via [ErrorReporter](https://github.com/JetBrains/intellij-ide-starter/blob/8c19f61989510def61e864515014d6e0df358342/intellij.tools.ide.starter/src/com/intellij/ide/starter/di/diContainer.kt#L51).
The default implementation
is [ErrorReporterToCI](https://github.com/JetBrains/intellij-ide-starter/blob/8c19f61989510def61e864515014d6e0df358342/intellij.tools.ide.starter/src/com/intellij/ide/starter/report/ErrorReporterToCI.kt#L15).

If you want to customize head of the error message you can do that via your own implementation
of [FailureDetailsOnCi](https://github.com/JetBrains/intellij-ide-starter/blob/8c19f61989510def61e864515014d6e0df358342/intellij.tools.ide.starter/src/com/intellij/ide/starter/report/FailureDetailsOnCI.kt#L10),
which is also registered via DI.

### Debugging the test

TIP: If you enable `debugger.auto.attach.from.console` Registry, you can just click debug on the test in IntelliJ IDEA and everything will
happen automatically.

Since tests are executed inside the IDE as an external process for test, you cannot directly debug them.
To debug a test, you need to connect remotely to the IDE instance.

General debugging workflow:

1. Create run configuration for Remote JVM Debug:
   Debugger mode: **Attach to Remote JVM**   
   Host: **localhost** Port: **5005**  
   Command line arguments for remote JVM: ```-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005```
2. Run your test. The required option will be added automatically.

After seeing the console prompt to connect remotely to port 5005, run the created run configuration.

### Using JUnit5 extensions to modify Starter behavior

For JUnit5, there are several extensions, which provide a convenient way to set configuration variables as needed.
List of
extensions [can be found here](https://github.com/JetBrains/intellij-ide-starter/tree/master/intellij.tools.ide.starter.junit5/src/com/intellij/ide/starter/junit5/config)

Example:

```
@ExtendWith(EnableClassFileVerification::class)
@ExtendWith(UseLatestDownloadedIdeBuild::class)
class ClassWithTest {
...
}
```

Also you might find useful environment variables, that can tweak Starter behavior.  
They are located in configuration storage in `com.intellij.ide.starter.config.StarterConfigurationStorage`

### Downloading custom releases

By default, when useEAP or useRelease methods are called, IDE installers will be downloaded from JetBrains' public hosting. If no version is
specified, the latest version will be used. However, you can specify a desired version if needed.

### How to specify another URL for IDE downloading

1. You need to override `IdeDownloader` from default downloader to downloader which looks into the `downloadURI` field in `IdeInfo`:

```kotlin
init {
  di = DI {
    extend(di)
    bindSingleton<IdeDownloader>(overrides = true) { IdeByLinkDownloader }
  }
}
```

2. You need to provide custom `IdeInfo` with the URL of your choice

```kotlin
Starter.newContext(
  CurrentTestMethod.hyphenateWithClass(), TestCase(
  IdeProductProvider.IU.copy(downloadURI = URI("www.example.com")), GitHubProject.fromGithub(
  branchName = "master",
  repoRelativeUrl = "jitpack/gradle-simple.git"
)
)
)
```

`IdeProductProvider.IU.copy(downloadURI = URI("[www.example.com](http://www.example.com)"))` - this is the main part, we say that we will
use `IU` (Idea Ultimate) so we copy all the properties except that we also provide URL from which it should be dowloaded.

### Modifying VM Options

There are two ways to modify the VM options. One is on `IDETestContext` and another on `IDERunContext`. The first one is used to modify
VM options for the whole context that can be reused between runs. The second is used to modify VM options just for the current run.

### Performance testing/Metrics collection

Out of the box, Starter can collect OpenTelemetry metrics
using [intellij.tools.ide.metrics.collector.starter](https://github.com/JetBrains/intellij-ide-starter/tree/master/intellij.tools.ide.metrics.collector.starter#readme)
module.

If you're interested in a more general approach to OpenTelemetry metrics collection (without Starter involved),
you can look
at [intellij.tools.ide.metrics.collector](https://github.com/JetBrains/intellij-community/tree/master/tools/intellij.tools.ide.metrics.collector#readme).

There is also an option to run unit tests as a benchchmark tests
via [Benchmark.newBenchmark(...)](https://github.com/JetBrains/intellij-community/blob/2067fd81905bd789332e206d2be4ef007b133c76/tools/intellij.tools.ide.metrics.benchmark/src/com/intellij/tools/ide/metrics/benchmark/Benchmark.java#L31).  
Examples [of usages in IntelliJ repo](https://github.com/search?q=repo%3AJetBrains%2Fintellij-community%20Benchmark.newBenchmark&type=code).
  
More details can be found
in [com.intellij.testFramework.BenchmarkTestInfo#start()](https://github.com/JetBrains/intellij-community/blob/7fe480df8be14f0c7de59fcdb56ac5bf056b24b6/platform/testFramework/src/com/intellij/testFramework/BenchmarkTestInfo.java#L66),
[com.intellij.testFramework.BenchmarkTestInfo#startAsSubtest()](https://github.com/JetBrains/intellij-community/blob/7fe480df8be14f0c7de59fcdb56ac5bf056b24b6/platform/testFramework/src/com/intellij/testFramework/BenchmarkTestInfo.java#L76),
[com.intellij.testFramework.BenchmarkTestInfoImpl#withMetricsCollector()](https://github.com/JetBrains/intellij-community/blob/7fe480df8be14f0c7de59fcdb56ac5bf056b24b6/tools/intellij.tools.ide.metrics.benchmark/src/com/intellij/tools/ide/metrics/benchmark/BenchmarkTestInfoImpl.java#L207),
