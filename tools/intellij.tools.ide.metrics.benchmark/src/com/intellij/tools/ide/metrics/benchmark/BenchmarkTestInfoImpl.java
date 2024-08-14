// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.metrics.benchmark;

import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.platform.diagnostic.telemetry.IJTracer;
import com.intellij.platform.diagnostic.telemetry.Scope;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.platform.diagnostic.telemetry.helpers.TraceKt;
import com.intellij.testFramework.BenchmarkTestInfo;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ProfilerForTests;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.tools.ide.metrics.collector.MetricsCollector;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.StorageLockContext;
import kotlin.reflect.KFunction;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.SupervisorKt;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

public class BenchmarkTestInfoImpl implements BenchmarkTestInfo {
  private enum IterationMode {
    WARMUP,
    MEASURE
  }

  private ThrowableComputable<Integer, ?> test;   // runnable to measure; returns actual input size
  private int expectedInputSize;                  // size of input the test is expected to process;
  private ThrowableRunnable<?> setup;                   // to run before each test
  private int maxMeasurementAttempts = 3;               // number of retries
  public String launchName;                      // to print on fail
  private int warmupIterations = 1;                      // default warmup iterations should be positive
  private String uniqueTestName;                        // at least full qualified test name (plus other identifiers, optionally)
  @NotNull
  private final IJTracer tracer;
  private final ArrayList<MetricsCollector> metricsCollectors = new ArrayList<>();

  private boolean useDefaultSpanMetricExporter = true;

  /**
   * Default span metrics exporter {@link BenchmarksSpanMetricsCollector} will not be used
   */
  public BenchmarkTestInfoImpl disableDefaultSpanMetricsExporter() {
    this.useDefaultSpanMetricExporter = false;
    return this;
  }

  private static final CoroutineScope coroutineScope = CoroutineScopeKt.CoroutineScope(
    SupervisorKt.SupervisorJob(null).plus(Dispatchers.getIO())
  );

  static {
    // to use JobSchedulerImpl.getJobPoolParallelism() in tests which don't init application
    IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true);
  }

  private static void initOpenTelemetry() {
    // Open Telemetry file will be located at ../system/test/log/opentelemetry.json (alongside with open-telemetry-metrics.* files)
    System.setProperty("idea.diagnostic.opentelemetry.file",
                       PathManager.getLogDir().resolve("opentelemetry.json").toAbsolutePath().toString());

    var telemetryInstance = TelemetryManager.getInstance();

    // looks like telemetry manager is properly initialized
    if (telemetryInstance.hasSpanExporters()) return;

    System.err.printf(
      "%nTelemetry instance will be overriden since span exporters aren't registered. " +
      "This means your metrics (meters or spans), configured before any test execution will not be reported. " +
      "Consider using TestApplication that will setup proper instance of telemetry.%n");

    try {
      TelemetryManager.Companion.resetGlobalSdk();
      var telemetryClazz = Class.forName("com.intellij.platform.diagnostic.telemetry.impl.TelemetryManagerImpl");
      var instance = Arrays.stream(telemetryClazz.getDeclaredConstructors())
        .filter((it) -> it.getParameterCount() > 0).findFirst()
        .get()
        .newInstance(coroutineScope, true);

      TelemetryManager.Companion.forceSetTelemetryManager((TelemetryManager)instance);
    }
    catch (Throwable e) {
      System.err.println(
        "Couldn't setup TelemetryManager without TestApplication. Either test should use TestApplication or somewhere is a bug");
      e.printStackTrace();
    }
  }

  private static void cleanupOutdatedMetrics() {
    try {
      // force spans and meters to be exported and discarded to minimize interference of the same metric on different tests
      TelemetryManager.getInstance().resetExportersBlocking();

      // remove content of the previous tests from the idea.log
      IJPerfBenchmarksMetricsPublisher.Companion.truncateTestLog();

      var filesWithMetrics = Files.list(PathManager.getLogDir()).filter((it) ->
                                                                          it.toString().contains("-metrics") ||
                                                                          it.toString().contains("-meters")).toList();
      for (Path file : filesWithMetrics) {
        Files.deleteIfExists(file);
      }
    }
    catch (Exception e) {
      System.err.println(
        "Error during removing Telemetry files with meters before start of perf test. This might affect collected metrics value.");
      e.printStackTrace();
    }
  }

  public BenchmarkTestInfoImpl(@NotNull ThrowableComputable<Integer, ?> test, int expectedInputSize, @NotNull String launchName) {
    this();
    initialize(test, expectedInputSize, launchName);
  }

  public BenchmarkTestInfoImpl() {
    initOpenTelemetry();
    cleanupOutdatedMetrics();
    this.tracer = TelemetryManager.getInstance().getTracer(new Scope("performanceUnitTests", null));
  }

  @Override
  public BenchmarkTestInfoImpl initialize(@NotNull ThrowableComputable<Integer, ?> test,
                                          int expectedInputSize,
                                          @NotNull String launchName) {
    this.test = test;
    this.expectedInputSize = expectedInputSize;
    assert expectedInputSize > 0 : "Expected input size must be > 0. Was: " + expectedInputSize;
    this.launchName = launchName;
    return this;
  }

  @Override
  @Contract(pure = true) // to warn about not calling .start() in the end
  public BenchmarkTestInfoImpl setup(@NotNull ThrowableRunnable<?> setup) {
    assert this.setup == null;
    this.setup = setup;
    return this;
  }

  @Override
  @Contract(pure = true) // to warn about not calling .start() in the end
  public BenchmarkTestInfoImpl attempts(int attempts) {
    this.maxMeasurementAttempts = attempts;
    return this;
  }

  /**
   * Instruct to publish Telemetry meters (stored in .json files)
   * Eg:
   * <pre>
   *   {@code
   *     val counter: AtomicLong = AtomicLong()
   *     val counterMeter = TelemetryManager.getMeter(MY_SCOPE)
   *       .counterBuilder("custom.counter")
   *       .buildWithCallback { it.record(counter.get()) }
   *
   *     val meterCollector = OpenTelemetryJsonMeterCollector(MetricsSelectionStrategy.SUM) { it.name.contains("custom") }
   *
   *     Benchmark.newBenchmark("my perf test") {
   *       counter.incrementAndGet()
   *     }
   *       .withMetricsCollector(meterCollector)
   *       .start()}
   * </pre>
   */
  @Contract(pure = true) // to warn about not calling .start() in the end
  public BenchmarkTestInfoImpl withMetricsCollector(MetricsCollector meterCollector) {
    this.metricsCollectors.add(meterCollector);
    return this;
  }

  @Override
  @Contract(pure = true) // to warn about not calling .start() in the end
  public BenchmarkTestInfoImpl warmupIterations(int iterations) {
    warmupIterations = iterations;
    return this;
  }

  @Override
  public String getUniqueTestName() {
    return uniqueTestName;
  }

  private static Method filterMethodFromStackTrace(Function<Method, Boolean> methodFilter) {
    StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();

    for (StackTraceElement element : stackTraceElements) {
      try {
        Method foundMethod = ContainerUtil.find(
          Class.forName(element.getClassName()).getDeclaredMethods(),
          method -> method.getName().equals(element.getMethodName()) && methodFilter.apply(method)
        );
        if (foundMethod != null) return foundMethod;
      }
      catch (ClassNotFoundException e) {
        // do nothing, continue
      }
    }
    return null;
  }

  private static Method tryToFindCallingTestMethodByJUnitAnnotation() {
    return filterMethodFromStackTrace(
      method -> ContainerUtil.exists(method.getDeclaredAnnotations(), annotation -> annotation.annotationType().getName().contains("junit"))
    );
  }

  private static Method tryToFindCallingTestMethodByNamePattern() {
    return filterMethodFromStackTrace(method -> method.getName().toLowerCase(Locale.ROOT).startsWith("test"));
  }

  private static Method getCallingTestMethod() {
    Method callingTestMethod = tryToFindCallingTestMethodByJUnitAnnotation();

    if (callingTestMethod == null) {
      callingTestMethod = tryToFindCallingTestMethodByNamePattern();
      if (callingTestMethod == null) {
        throw new AssertionError(
          "Couldn't manage to detect the calling test method. Please use one of the overloads of com.intellij.testFramework.PerformanceTestInfo.start"
        );
      }
    }

    return callingTestMethod;
  }

  @Override
  public void start() {
    start(getCallingTestMethod(), launchName);
  }

  /**
   * Start the perf test where the test's artifact path will have a name inferred from test method + subtest name.
   *
   * @see BenchmarkTestInfoImpl#start()
   * @see BenchmarkTestInfoImpl#startAsSubtest(String)
   * @see BenchmarkTestInfoImpl#start(kotlin.reflect.KFunction)
   **/
  public void start(@NotNull Method javaTestMethod, String subTestName) {
    var fullTestName = String.format("%s.%s", javaTestMethod.getDeclaringClass().getName(), javaTestMethod.getName());
    if (subTestName != null && !subTestName.isEmpty()) {
      fullTestName += " - " + subTestName;
    }
    start(fullTestName);
  }

  public void start(@NotNull KFunction<?> kotlinTestMethod) {
    start(String.format("%s.%s", kotlinTestMethod.getClass().getName(), kotlinTestMethod.getName()));
  }


  @Override
  public void startAsSubtest() {
    startAsSubtest(launchName);
  }

  /**
   * The same as {@link #startAsSubtest()} but with the option to specify subtest name.
   */
  @Override
  public void startAsSubtest(@Nullable String subTestName) {
    start(getCallingTestMethod(), subTestName);
  }

  public void start(String fullQualifiedTestMethodName) {
    String sanitizedFullQualifiedTestMethodName = sanitizeFullTestNameForArtifactPublishing(fullQualifiedTestMethodName);
    start(IterationMode.WARMUP, sanitizedFullQualifiedTestMethodName);
    start(IterationMode.MEASURE, sanitizedFullQualifiedTestMethodName);
  }

  @Override
  public String getLaunchName() {
    return launchName;
  }

  /**
   * @param uniqueTestName - should be at least full qualified test method name.
   *                       Sometimes additional suffixes might be added like here {@link BenchmarkTestInfoImpl#startAsSubtest(String)}
   */
  private void start(IterationMode iterationType, String uniqueTestName) {
    this.uniqueTestName = uniqueTestName;

    if (PlatformTestUtil.COVERAGE_ENABLED_BUILD) return;
    System.out.printf("Starting performance test \"%s\" in mode: %s%n", uniqueTestName, iterationType);

    int maxIterationsNumber;
    if (iterationType.equals(IterationMode.WARMUP)) {
      maxIterationsNumber = warmupIterations;
    }
    else {
      maxIterationsNumber = maxMeasurementAttempts;
    }

    if (maxIterationsNumber == 1) {
      //noinspection CallToSystemGC
      System.gc();
    }

    try {
      TraceKt.use(tracer.spanBuilder(uniqueTestName).setAttribute("warmup", String.valueOf(iterationType.equals(IterationMode.WARMUP))),
                  __ -> {
                    try {
                      PlatformTestUtil.waitForAllBackgroundActivityToCalmDown();

                      for (int attempt = 1; attempt <= maxIterationsNumber; attempt++) {
                        AtomicInteger actualInputSize;

                        if (setup != null) setup.run();
                        actualInputSize = new AtomicInteger(expectedInputSize);

                        Supplier<Object> perfTestWorkload = getPerfTestWorkloadSupplier(iterationType, attempt, actualInputSize);

                        TraceKt.use(tracer.spanBuilder("Attempt: " + attempt)
                                      .setAttribute("warmup", String.valueOf(iterationType.equals(IterationMode.WARMUP))),
                                    ignore -> perfTestWorkload.get());

                        if (!UsefulTestCase.IS_UNDER_TEAMCITY) {
                          // TODO: Print debug metrics here https://youtrack.jetbrains.com/issue/AT-726
                        }
                        //noinspection CallToSystemGC
                        System.gc();
                        StorageLockContext.forceDirectMemoryCache();
                      }
                    }
                    catch (Throwable throwable) {
                      ExceptionUtil.rethrowUnchecked(throwable);
                      throw new RuntimeException(throwable);
                    }

                    return null;
                  });
    }
    finally {
      try {
        // publish warmup and final measurements at once at the end of the runs
        if (iterationType.equals(IterationMode.MEASURE)) {
          var collectors = new ArrayList<MetricsCollector>();
          if (useDefaultSpanMetricExporter) {
            collectors.add(new BenchmarksSpanMetricsCollector(uniqueTestName,
                                                              BenchmarksSpanMetricsCollector.Companion.getDefaultPathToTelemetrySpanJson()));
          }
          collectors.addAll(metricsCollectors);

          IJPerfBenchmarksMetricsPublisher.Companion.publishSync(uniqueTestName, collectors.toArray(new MetricsCollector[0]));
        }
      }
      catch (Throwable t) {
        System.err.println("Something unexpected happened during publishing performance metrics");
        throw t;
      }
    }
  }

  private @NotNull Supplier<Object> getPerfTestWorkloadSupplier(IterationMode iterationType, int attempt, AtomicInteger actualInputSize) {
    return () -> {
      try {
        Profiler.startProfiling(iterationType.name() + attempt);
        actualInputSize.set(test.compute());
      }
      catch (Throwable e) {
        ExceptionUtil.rethrowUnchecked(e);
        throw new RuntimeException(e);
      }
      finally {
        Profiler.stopProfiling();
      }

      return null;
    };
  }

  private static @NotNull String sanitizeFullTestNameForArtifactPublishing(@NotNull String fullTestName) {
    try {
      //noinspection ResultOfMethodCallIgnored
      Path.of("./" + fullTestName); // prefix with "./" to make sure "C:/Users" is sanitized
      return fullTestName;
    }
    catch (InvalidPathException e) {
      return FileUtil.sanitizeFileName(fullTestName, false);
    }
  }

  private static final class Profiler {
    private static final ProfilerForTests profiler = getProfilerInstance();

    private static ProfilerForTests getProfilerInstance() {
      ServiceLoader<ProfilerForTests> loader = ServiceLoader.load(ProfilerForTests.class);
      for (ProfilerForTests service : loader) {
        if (service != null) {
          return service;
        }
      }
      System.out.println("No service com.intellij.testFramework.Profiler is found in class path");
      return null;
    }

    public static void stopProfiling() {
      if (profiler != null) {
        try {
          profiler.stopProfiling();
        }
        catch (IOException e) {
          System.out.println("Can't stop profiling");
        }
      }
    }

    public static void startProfiling(String fileName) {
      Path logDir = PathManager.getLogDir();
      if (profiler != null) {
        try {
          profiler.startProfiling(logDir, fileName);
        }
        catch (IOException e) {
          System.out.println("Can't start profiling");
        }
      }
    }
  }
}
