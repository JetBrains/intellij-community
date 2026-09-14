// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.python.PyHelper
import com.intellij.python.community.execService.python.StdInProvider
import com.intellij.python.sdk.backend.getPythonInfo
import com.intellij.python.sdk.backend.pythonInterpreter
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.Result
import com.jetbrains.python.documentation.docstrings.DocStringFormat
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.executeHelper
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting

internal object PyRuntimeDocstringFormatter {

  private val helper = PyHelper("docstring_formatter.py", addDependency = true)

  internal sealed interface ModuleOrSdk {
    data class TheSdk(val sdk: Sdk) : ModuleOrSdk
    data class TheModule(val module: Module) : ModuleOrSdk
  }

  @RequiresBackgroundThread
  fun runExternalTool(moduleOrSdk: ModuleOrSdk, format: DocStringFormat, input: String, formatterFlags: List<String>): String? {
    val sdk = when (moduleOrSdk) {
      is ModuleOrSdk.TheSdk -> moduleOrSdk.sdk
      is ModuleOrSdk.TheModule -> {
        PythonSdkType.findLocalCPython(moduleOrSdk.module) ?: return logSdkNotFound(format)
      }
    }
    val languageLevel = sdk.pythonInterpreter().getPythonInfo().getOr {
      LOG.debug { "Sdk $sdk is broken ${it.error}" }
      return null
    }.languageLevel
    return if (languageLevel.isPython2) {
      logPy2NotSupported()
    }
    else {
      formatCached(sdk.homePath!!, languageLevel, format, formatterFlags, input) {
        runProcess(sdk, format, formatterFlags, input)
      }
    }
  }

  @RequiresBackgroundThread
  private fun runProcess(sdk: Sdk, format: DocStringFormat, formatterFlags: List<String>, input: String): String? {
    val encodedInput = DEFAULT_CHARSET.encode(input)
    val data = ByteArray(encodedInput.limit()).also { encodedInput.get(it) }
    val arguments = formatterFlags.toMutableList().apply {
      add("--format")
      add(format.formatterCommand)
    }

    val stdInProvider = StdInProvider(data = data) {
      // If script started, but closed its input, it is 100% helper problem
      LOG.error("Failed to write to helper input", it)
    }
    val result = runBlockingMaybeCancellable {
      ExecService().executeHelper(
        sdk = sdk,
        helper = helper,
        helperArgs = Args(*arguments.toTypedArray()),
        stdInProvider = stdInProvider
      )
    }

    return when (result) {
      is Result.Success -> result.result
      is Result.Failure -> {
        LOG.warn("Error ${result.error} for input:\n$input")
        null
      }
    }
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

  private val LOG: Logger by lazy { Logger.getInstance(PyRuntimeDocstringFormatter::class.java) }
  private val DEFAULT_CHARSET = Charsets.UTF_8
}
