// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.target

import com.intellij.execution.target.LanguageRuntimeType
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

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
    val userHomeFuture = pythonExecutablePathFuture.thenCompose {
      subject
        .promiseEnvironmentVariable("HOME")
        .thenComposeIf(String?::isNullOrBlank) {
          subject
            .promiseExecuteScript("pwd")
            .thenApply { it?.firstOutputLine() }
        }
        .thenApply { value ->
          if (value != null) config.userHome = value
        }
    }
    return userHomeFuture.thenApply { config }
  }

  companion object {
    private const val WHICH_BINARY = "/usr/bin/which"

    private fun String.firstOutputLine(): String? = trim().let { trimmedOutput ->
      val iterator = trimmedOutput.lineSequence().iterator()
      if (iterator.hasNext()) iterator.next() else null
    }

    private fun <T> CompletableFuture<T>.thenComposeIf(predicate: (T) -> Boolean, fn: () -> CompletionStage<T>): CompletableFuture<T> =
      thenCompose { if (predicate(it)) fn() else this }
  }
}