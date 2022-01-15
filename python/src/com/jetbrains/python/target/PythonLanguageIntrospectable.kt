// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.target

import com.intellij.execution.target.LanguageRuntimeType
import java.util.concurrent.CompletableFuture

class PythonLanguageIntrospectable(val config: PythonLanguageRuntimeConfiguration) : LanguageRuntimeType.Introspector<PythonLanguageRuntimeConfiguration> {
  override fun introspect(subject: LanguageRuntimeType.Introspectable): CompletableFuture<PythonLanguageRuntimeConfiguration> {
    val pythonExecutable = "python3"
    val pythonExecutablePathFuture = subject
      .promiseExecuteScript("$WHICH_BINARY $pythonExecutable")
      .thenApply { result ->
        result?.trim()?.let { trimmedOutput ->
          val iterator = trimmedOutput.lineSequence().iterator()
          if (iterator.hasNext()) config.pythonInterpreterPath = iterator.next()
        }
      }
    val pwdExecutableFuture = pythonExecutablePathFuture.thenCompose {
      subject
        .promiseExecuteScript("pwd")
        .thenApply { result ->
          result?.useFirstOutputLine { firstLine ->
            config.userHome = firstLine
          }
        }
    }
    return pwdExecutableFuture.thenApply { config }
  }

  companion object {
    private const val WHICH_BINARY = "/usr/bin/which"

    private fun <R> String.useFirstOutputLine(block: (String) -> R): R? = trim().let { trimmedOutput ->
      val iterator = trimmedOutput.lineSequence().iterator()
      return@let if (iterator.hasNext()) block(iterator.next()) else null
    }
  }
}