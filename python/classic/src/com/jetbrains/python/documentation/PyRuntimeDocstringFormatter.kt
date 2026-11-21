// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.asBinToExec
import com.intellij.python.community.execService.python.PyHelper
import com.intellij.python.community.execService.python.executeHelper
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.binaryPathString
import com.intellij.python.sdk.backend.getPythonInfo
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.documentation.docstrings.DocStringFormat
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.pyRetryCommand
import com.jetbrains.python.sdk.executeHelper
import com.jetbrains.python.sdk.findLocalHelperPython
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting

@ApiStatus.Internal
object PyRuntimeDocstringFormatter {

  private val helper = PyHelper("docstring_formatter.py")

  /**
   * What runs the formatter: the interpreter that the caller gives, or the local helper Python for a module, see
   * [findLocalHelperPython].
   */
  @ApiStatus.Internal
  sealed interface ModuleOrInterpreter {
    data class TheInterpreter(val interpreter: PythonInterpreter) : ModuleOrInterpreter
    data class TheModule(val module: Module) : ModuleOrInterpreter
  }

  /** A Python that runs the formatter. [cacheKey] names it in [PyDocstringFormatterCache]. */
  private class FormatterPython(
    val cacheKey: String,
    val languageLevel: LanguageLevel,
    val execute: suspend (Args, ByteArray) -> PyResult<String>,
  )

  @RequiresBackgroundThread
  fun runExternalTool(target: ModuleOrInterpreter, format: DocStringFormat, input: String, formatterFlags: List<String>): String? {
    val python = when (target) {
      is ModuleOrInterpreter.TheInterpreter -> {
        val interpreter = target.interpreter
        val languageLevel = interpreter.getPythonInfo().getOr {
          LOG.debug { "$interpreter is broken ${it.error}" }
          return null
        }.languageLevel
        FormatterPython(interpreter.binaryPathString, languageLevel) { args, stdIn ->
          ExecService().executeHelper(interpreter = interpreter, helper = helper, helperArgs = args, dataForStdIn = stdIn)
        }
      }
      is ModuleOrInterpreter.TheModule -> {
        val module = target.module
        val local = runBlockingMaybeCancellable { findLocalHelperPython(module.project, module) } ?: return logSdkNotFound(format)
        FormatterPython(local.binary.toString(), local.languageLevel) { args, stdIn ->
          ExecService().executeHelper(python = local.binary.asBinToExec(), helper = helper, helperArgs = args, dataForStdIn = stdIn)
        }
      }
    }
    return if (python.languageLevel.isPython2) {
      logPy2NotSupported()
    }
    else {
      formatCached(python.cacheKey, python.languageLevel, format, formatterFlags, input) {
        runProcess(python, format, formatterFlags, input)
      }
    }
  }

  @RequiresBackgroundThread
  private fun runProcess(python: FormatterPython, format: DocStringFormat, formatterFlags: List<String>, input: String): String {
    val encodedInput = DEFAULT_CHARSET.encode(input)
    val data = ByteArray(encodedInput.limit()).also { encodedInput.get(it) }
    val arguments = formatterFlags.toMutableList().apply {
      add("--format")
      add(format.formatterCommand)
    }

    return runBlockingMaybeCancellable {
      pyRetryCommand(LOG, { "docstring formatter on ${python.cacheKey}" }) {
        python.execute(Args(*arguments.toTypedArray()), data)
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
