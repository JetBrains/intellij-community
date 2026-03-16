package com.intellij.tools.ide.metrics.benchmark;

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.testFramework.BenchmarkTestInfo;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class Benchmark {
  /// Initializes a performance test.
  ///
  /// Example:
  /// ```
  /// newBenchmark("calculating pi", () -> {
  ///   // CODE_TO_BE_MEASURED_IS_HERE
  /// }).start();
  /// ```
  /// ### Beware
  ///
  /// If you start a benchmark in a unit test, ensure `ApplicationManagerEx.setInStressTest(true)` is called.
  ///
  /// For JUnit 5 tests, use [com.intellij.testFramework.junit5.StressTestApplication] instead of plain [com.intellij.testFramework.junit5.TestApplication].
  /// For JUnit 3/4, use setUp/tearDown methods.
  ///
  /// Without `inStressTest=true`, a lot of debug checks are executed.
  /// Most important of them is `logLevel=DEBUG` - this makes benchmark results inadequate.
  ///
  /// @see BenchmarkTestInfo#start()
  @Contract(pure = true) // to warn about not calling .assertTiming() in the end
  public static @NotNull BenchmarkTestInfoImpl newBenchmark(@NonNls @NotNull String launchName, @NotNull ThrowableRunnable<?> test) {
    return newBenchmarkWithVariableInputSize(launchName, 1, () -> {
      test.run();
      return 1;
    });
  }

  /// Initializes a performance test whose inputs may change.
  ///
  /// For example, it may depend on the number of files in the project.
  /// ### Beware
  ///
  /// If you start a benchmark in a unit test, ensure `ApplicationManagerEx.setInStressTest(true)` is called.
  ///
  /// For JUnit 5 tests, use [com.intellij.testFramework.junit5.StressTestApplication] instead of plain [com.intellij.testFramework.junit5.TestApplication].
  /// For JUnit 3/4, use setUp/tearDown methods.
  ///
  /// Without `inStressTest=true`, a lot of debug checks are executed.
  /// Most important of them is `logLevel=DEBUG` - this makes benchmark results inadequate.
  ///
  /// @param expectedInputSize specifies the size of the input
  /// @param test              returns actual size of the input. It is supposed that the execution time is lineally proportionally dependent on the input size.
  /// @see BenchmarkTestInfo#start()
  @Contract(pure = true)
  public static @NotNull BenchmarkTestInfoImpl newBenchmarkWithVariableInputSize(@NonNls @NotNull String launchName,
                                                                                 int expectedInputSize,
                                                                                 @NotNull ThrowableComputable<Integer, ?> test) {
    return new BenchmarkTestInfoImpl(test, expectedInputSize, launchName);
  }
}
