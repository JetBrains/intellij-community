// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.validation

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.ZeroCodeStdoutTransformerTyped
import com.intellij.python.community.execService.asBinToExec
import com.intellij.python.community.execService.python.PyHelper
import com.intellij.python.community.execService.python.StdInProvider
import com.intellij.python.community.execService.python.executeHelper
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result

@RequiresBackgroundThread
internal fun execPep8Helper(helper: PyHelper, python: PythonBinary, stdin: ByteArray, options: List<String>): String? {
  val res = runBlockingMaybeCancellable {
    ExecService().executeHelper(python.asBinToExec(python.parent.asEelPath()),
                                helper,
                                Args(*options.toTypedArray()),
      // Helper returns != 0, expected
                                execOptions,
                                stdInProvider = StdInProvider(stdin),
                                processOutputTransformer = ZeroCodeStdoutTransformerTyped(checkExitCode = false) { it })
  }
  return when (res) {
    is Result.Failure -> {
      log.warn("Error running $helper: ${res.error.message}")
      null
    }
    is Result.Success -> res.result.trim()
  }
}

private val log = fileLogger()
/**
 * `PYTHONUTF8` keeps PY-37054 fixed. The helper reads stdin with `io.TextIOWrapper` and no explicit encoding, so it
 * falls back to the locale encoding. That is the ANSI code page on Windows, and then `E501` counts UTF-8 bytes
 * instead of characters. UTF-8 mode makes that fallback UTF-8, and it also makes the helper write the report in UTF-8.
 */
private val execOptions = ExecOptions(env = mapOf("PYTHONBUFFERED" to "1", "PYTHONUTF8" to "1"))
