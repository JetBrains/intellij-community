// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.editing

import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.intellij.tools.ide.metrics.benchmark.BenchmarkTestInfoImpl
import com.intellij.util.ThrowableRunnable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import java.lang.reflect.Method

/**
 * The base of the YAML performance tests of IJPL-63225.
 * A benchmark of a test has the name of the test method without the `test` prefix.
 */
abstract class YamlPerformanceTestCase {
  private lateinit var testMethod: Method

  @BeforeEach
  fun rememberTestMethod(testInfo: TestInfo) {
    testMethod = testInfo.testMethod.get()
  }

  /** Creates the benchmark of the current test. */
  protected fun benchmark(test: ThrowableRunnable<*>): BenchmarkTestInfoImpl = Benchmark.newBenchmark(benchmarkName(), test)

  /**
   * Starts the benchmark with the current test method.
   * The benchmark cannot find the method from the stack of a test that runs on the EDT.
   */
  protected fun BenchmarkTestInfoImpl.startForTest() = start(testMethod, benchmarkName())

  private fun benchmarkName(): String = testMethod.name.removePrefix("test")
}

/** Builds an OpenAPI file with [items] items. Each item has about 430 chars. */
internal fun openApiText(items: Int): String = buildString {
  append("openapi: \"3.0.3\"\n")
  append("paths:\n")
  for (i in 0 until items) {
    append("  /items/$i:\n")
    append("    get:\n")
    append("      summary: \"Get the item $i\"\n")
    append("      description: \"Returns the item $i with all its fields. It is a long text of the description.\"\n")
    append("      # The comment of the operation $i\n")
    append("      operationId: get-item-$i\n")
    append("      x-note: plain text of the item $i\n")
    append("      x-text: |\n")
    append("        block text of the item $i\n")
    append("      x-flow: {first: one, second: two}\n")
    append("      responses:\n")
    append("        \"200\":\n")
    append("          description: OK\n")
  }
}
