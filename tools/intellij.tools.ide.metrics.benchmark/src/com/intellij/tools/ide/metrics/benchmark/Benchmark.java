package com.intellij.tools.ide.metrics.benchmark;

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.testFramework.BenchmarkTestInfo;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class Benchmark {
  /**
   * Init a performance test.<br/>
   * E.g: {@code newBenchmark("calculating pi", () -> { CODE_TO_BE_MEASURED_IS_HERE }).start();}
   *
   * @see BenchmarkTestInfo#start()
   */
  // to warn about not calling .assertTiming() in the end
  @Contract(pure = true)
  public static @NotNull BenchmarkTestInfoImpl newBenchmark(@NonNls @NotNull String launchName,
                                                            @NotNull ThrowableRunnable<?> test) {
    return newBenchmarkWithVariableInputSize(launchName, 1, () -> {
      test.run();
      return 1;
    });
  }

  /**
   * Init a performance test which input may change.<br/>
   * E.g: it depends on the number of files in the project.
   * <p>
   *
   * @param expectedInputSize specifies size of the input,
   * @param test              returns actual size of the input. It is supposed that the execution time is lineally proportionally dependent on the input size.
   * @see BenchmarkTestInfo#start()
   * </p>
   */
  @Contract(pure = true)
  public static @NotNull BenchmarkTestInfoImpl newBenchmarkWithVariableInputSize(@NonNls @NotNull String launchName,
                                                                                 int expectedInputSize,
                                                                                 @NotNull ThrowableComputable<Integer, ?> test) {
    return new BenchmarkTestInfoImpl(test, expectedInputSize, launchName);
  }
}
