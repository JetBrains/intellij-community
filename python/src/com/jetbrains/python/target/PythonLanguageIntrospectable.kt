// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.target

import com.intellij.execution.target.LanguageRuntimeType
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.function.BiFunction

class PythonLanguageIntrospectable(val config: PythonLanguageRuntimeConfiguration) : LanguageRuntimeType.Introspector<PythonLanguageRuntimeConfiguration> {
  override fun introspect(subject: LanguageRuntimeType.Introspectable): CompletableFuture<PythonLanguageRuntimeConfiguration> {
    val pythonExecutable = "python3"
    val pythonExecutablePathFuture = subject
      .promiseExecuteScript("$WHICH_BINARY $pythonExecutable")
      .thenApply { it?.firstOutputLine()?.let { firstStdoutLine -> config.pythonInterpreterPath = firstStdoutLine } }
    val userHomeFuture = pythonExecutablePathFuture.thenExecute {
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

    /**
     * Discards the return value and ignores any exception during the execution of [this] future and invokes [fn].
     *
     * Please note that [com.intellij.execution.target.LanguageRuntimeType.Introspectable.promiseExecuteScript] does not provide the exit
     * code of the script in the return value. Moreover, its implementations treats non-zero exit code as an error and throws an exception
     * in this case.
     *
     * However, getting non-zero exit code in some cases are perfectly normal. F.e. getting the exit code `1` in case when a Python
     * executable cannot be found using `which python3` command is fine and this case could be handled properly.
     */
    private fun <T, U> CompletableFuture<T>.thenExecute(fn: () -> CompletionStage<U>): CompletableFuture<U> =
      handle(BiFunction { _, _ -> null }).thenCompose { fn() }
  }
}