// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.documentation.docstrings.DocStringFormat
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PySdkUtil
import com.jetbrains.python.sdk.PySdkUtil.getLanguageLevelForSdk
import com.jetbrains.python.sdk.PythonSdkType
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path

object PyRuntimeDocstringFormatter {
  fun runExternalTool(module: Module, format: DocStringFormat, input: String, formatterFlags: List<String>): String? {
    val sdk = PythonSdkType.findLocalCPython(module) ?: return logSdkNotFound(format)
    val languageLevel = getLanguageLevelForSdk(sdk)
    if (languageLevel.isPython2) {
      return logPy2NotSupported()
    }
    val sdkHome = sdk.homePath ?: return null

    return formatCached(sdkHome, languageLevel, format, formatterFlags, input) {
      runProcess(sdk, sdkHome, format, formatterFlags, input)
    }
  }

  private fun runProcess(sdk: Sdk, sdkHome: String, format: DocStringFormat, formatterFlags: List<String>, input: String): String? {
    val encodedInput = DEFAULT_CHARSET.encode(input)
    val data = ByteArray(encodedInput.limit()).also { encodedInput.get(it) }
    val arguments = formatterFlags.toMutableList().apply {
      add("--format")
      add(format.formatterCommand)
    }

    val commandLine = PythonHelper.DOCSTRING_FORMATTER
      .newCommandLine(sdk, arguments)
      .withCharset(DEFAULT_CHARSET)

    LOG.debug("Command for launching docstring formatter: ${commandLine.commandLineString}")

    val output = PySdkUtil.getProcessOutput(commandLine, Path.of(sdkHome).parent?.toString(),
                                            null, 5000, data, false)

    return if (output.checkSuccess(LOG)) {
      output.stdout
    }
    else logScriptError(input)
  }

  @VisibleForTesting
  fun formatCached(
    sdkHome: String,
    languageLevel: LanguageLevel,
    format: DocStringFormat,
    formatterFlags: List<String>,
    input: String,
    cache: PyDocstringFormatterCache = PyDocstringFormatterCache.getInstance(),
    compute: () -> String?,
  ): String? {
    val key = PyDocstringFormatterCache.Key(sdkHome, languageLevel, format.formatterCommand, formatterFlags, input)
    return cache.getOrCompute(key, compute)
  }

  private fun logErrorToJsonBody(@Nls message: String): String {
    return Gson().toJson(
      PyDocumentationBuilder.DocstringFormatterRequest(
        HtmlChunk.p().attr("color", ColorUtil.toHtmlColor(JBColor.RED)).addRaw(message).toString()))
  }

  private fun logPy2NotSupported(): String {
    val message = PyPsiBundle.message("QDOC.python.3.sdk.needed.to.render.docstrings")
    LOG.warn(message)
    return logErrorToJsonBody(message)
  }

  private fun logSdkNotFound(format: DocStringFormat): String {
    LOG.warn("Python SDK for input formatter $format is not found")
    return logErrorToJsonBody(PyPsiBundle.message("QDOC.python.3.sdk.needed.to.render.docstrings"))
  }

  private fun logScriptError(input: String): String? {
    LOG.warn("Malformed input or internal script error:\n$input")
    return null
  }

  private val LOG: Logger by lazy { Logger.getInstance(PyRuntimeDocstringFormatter::class.java) }
  private val DEFAULT_CHARSET = Charsets.UTF_8
}
