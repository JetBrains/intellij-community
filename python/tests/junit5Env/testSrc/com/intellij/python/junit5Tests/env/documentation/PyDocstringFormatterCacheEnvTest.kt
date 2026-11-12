// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.documentation

import com.intellij.python.sdk.backend.detectedEnvironment
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pyInterpreterFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.io.write
import com.jetbrains.python.documentation.PyDocstringFormatterCache
import com.jetbrains.python.documentation.PyRuntimeDocstringFormatter
import com.jetbrains.python.documentation.PyRuntimeDocstringFormatter.runExternalTool
import com.jetbrains.python.documentation.docstrings.DocStringFormat
import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import com.intellij.testFramework.junit5.fixture.projectFixture

@PyEnvTestCase
internal class PyDocstringFormatterCacheEnvTest {
  private val projectFixture = projectFixture()
  private val sdkFixture by projectFixture.pyProjectFixture().pyInterpreterFixture()

  @Test
  fun `identical docstring is formatted by the real interpreter only once`(
    @TempDir tempDir: Path,
  ): Unit = timeoutRunBlocking {
    val cache = PyDocstringFormatterCache()
    val formatterInvocations = AtomicInteger()
    val inputFile = tempDir.resolve("input.txt")
    inputFile.write(INPUT)

    fun format(): String? = PyRuntimeDocstringFormatter.formatCached(
      sdkHome = sdkFixture.interpreter.detectedEnvironment!!.pythonBinaryPath.toString(),
      languageLevel = LanguageLevel.PYTHON312,
      format = DocStringFormat.REST,
      formatterFlags = emptyList(),
      input = INPUT,
      cache = cache,
    ) {
      formatterInvocations.incrementAndGet()
      runBlockingMaybeCancellable { runRealFormatter() }
    }

    val first = format()
    val second = format()

    Assertions.assertNotNull(first, "The real docstring formatter should have produced output")
    Assertions.assertTrue(first!!.contains("def hello():"), "Formatter output should contain the rendered code, but was:\n$first")
    Assertions.assertEquals(first, second, "Cached call should return the same result")
    Assertions.assertEquals(1, formatterInvocations.get(),
                            "The external formatter must run exactly once for identical input; the second call must be served from the cache")
  }

  private fun runRealFormatter(): String? =
    runExternalTool(PyRuntimeDocstringFormatter.ModuleOrSdk.TheSdk(@Suppress("DEPRECATION") sdkFixture.interpreter.getSdkAPI()),
                    DocStringFormat.REST,
                    INPUT,
                    emptyList())

  companion object {
    private val INPUT = """
      Example function.

      .. code-block:: python

         def hello():
             x = 1
             print(x)

      Back to normal text.
    """.trimIndent()
  }
}
