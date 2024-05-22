package com.intellij.tools.ide.metrics.benchmark;

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.testFramework.PerformanceTestInfo;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class PerformanceTestUtil {
  /**
   * Init a performance test.<br/>
   * E.g: {@code newPerformanceTest("calculating pi", () -> { CODE_TO_BE_MEASURED_IS_HERE }).start();}
   *
   * @see PerformanceTestInfo#start()
   */
  // to warn about not calling .assertTiming() in the end
  @Contract(pure = true)
  public static @NotNull PerformanceTestInfoImpl newPerformanceTest(@NonNls @NotNull String launchName,
                                                                    @NotNull ThrowableRunnable<?> test) {
    return newPerformanceTestWithVariableInputSize(launchName, 1, () -> {
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
   * @see PerformanceTestInfo#start()
   * </p>
   */
  @Contract(pure = true)
  public static @NotNull PerformanceTestInfoImpl newPerformanceTestWithVariableInputSize(@NonNls @NotNull String launchName,
                                                                                         int expectedInputSize,
                                                                                         @NotNull ThrowableComputable<Integer, ?> test) {
    return new PerformanceTestInfoImpl(test, expectedInputSize, launchName);
  }
}
