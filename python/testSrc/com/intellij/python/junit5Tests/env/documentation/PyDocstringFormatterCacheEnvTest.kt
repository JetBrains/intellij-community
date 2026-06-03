// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.documentation

import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.asBinToExec
import com.intellij.python.community.execService.python.executeHelper
import com.intellij.python.community.helpersLocator.PythonHelpersLocator
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.io.write
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.documentation.PyDocstringFormatterCache
import com.jetbrains.python.documentation.PyRuntimeDocstringFormatter
import com.jetbrains.python.documentation.docstrings.DocStringFormat
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.getOrThrow
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

@PyEnvTestCase
class PyDocstringFormatterCacheEnvTest {

  @Test
  fun `identical docstring is formatted by the real interpreter only once`(
    @PythonBinaryPath python: PythonBinary,
    @TempDir tempDir: Path,
  ): Unit = timeoutRunBlocking {
    val cache = PyDocstringFormatterCache()
    val formatterInvocations = AtomicInteger()
    val inputFile = tempDir.resolve("input.txt")
    inputFile.write(INPUT)

    fun format(): String? = PyRuntimeDocstringFormatter.formatCached(
      sdkHome = python.toString(),
      languageLevel = LanguageLevel.PYTHON312,
      format = DocStringFormat.REST,
      formatterFlags = emptyList(),
      input = INPUT,
      cache = cache,
    ) {
      formatterInvocations.incrementAndGet()
      runBlockingMaybeCancellable { runRealFormatter(python, inputFile) }
    }

    val first = format()
    val second = format()

    Assertions.assertNotNull(first, "The real docstring formatter should have produced output")
    Assertions.assertTrue(first!!.contains("def hello():"), "Formatter output should contain the rendered code, but was:\n$first")
    Assertions.assertEquals(first, second, "Cached call should return the same result")
    Assertions.assertEquals(1, formatterInvocations.get(),
                            "The external formatter must run exactly once for identical input; the second call must be served from the cache")
  }

  private suspend fun runRealFormatter(python: PythonBinary, inputFile: Path): String {
    return ExecService().executeHelper(
      python.asBinToExec(),
      "docstring_formatter.py",
      listOf("--format", "rest", "--input", inputFile.toString()),
      ExecOptions(env = mapOf("PYTHONPATH" to PythonHelpersLocator.getCommunityHelpersRoot().resolve("py3only").toString())),
    ).getOrThrow()
  }

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
