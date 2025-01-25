// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.util

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.PyBundle
import com.jetbrains.python.Result
import com.jetbrains.python.Result.Failure
import com.jetbrains.python.Result.Success
import com.jetbrains.python.execution.PyExecutionFailure
import com.jetbrains.python.execution.userMessage
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.showProcessExecutionErrorDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls

/**
 * [FlowCollector.emit] user-readable errors here.
 *
 * This class should be used by the topmost classes, tightly coupled to the UI.
 * For the most business-logic and backend functions please return [com.jetbrains.python.Result] or error.
 *
 * Please do not report *all* exceptions here: This is *not* the class for NPEs and AOOBs:
 * do not pass exceptions caught by `catch(e: Exception)` or `runCatching`: only report exceptions user interested in.
 * `IOException` or `ExecutionException` are generally ok.
 *
 * There will be unified sink soon to show and log errors.
 * Currently, only [ShowingMessageErrorSync] is a well-known implementation
 *
 * Example:
 * ```kotlin
 *  suspend fun someLogic(): Result<@NlsSafe String, IOException> = withContext(Dispatchers.IO) {
 *   try {
 *     Result.success(Path.of("1.txt").readText())
 *   }
 *   catch (e: IOException) {
 *     Result.failure(e)
 *   }
 * }
 *
 * suspend fun ui(errorSink: ErrorSink) {
 *   someLogic()
 *     .onSuccess {
 *       Messages.showInfoMessage("..", it)
 *     }
 *     .onFailure {
 *       errorSink.emit(it.localizedMessage)
 *     }
 * }
 * ```
 */
typealias ErrorSink = FlowCollector<PyError>


sealed class PyError(val message: @NlsSafe String) {
  /**
   * Some "business" error: just a message to be displayed to a user
   */
  class Message(message: @NlsSafe String) : PyError(message)

  /**
   * Some process can't be executed. To be displayed specially.
   */
  data class ExecException(val execFailure: PyExecutionFailure) : PyError(execFailure.userMessage)

  override fun toString(): String = message
}

/**
 * Displays error with a message box and writes it to a log.
 */
internal object ShowingMessageErrorSync : ErrorSink {
  override suspend fun emit(error: PyError) {
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      thisLogger().warn(error.message)
      // Platform doesn't allow dialogs without lock for now, fix later
      writeIntentReadAction {
        when (val e = error) {
          is PyError.ExecException -> {
            showProcessExecutionErrorDialog(null, e.execFailure)
          }
          is PyError.Message -> {
            Messages.showErrorDialog(error.message, PyBundle.message("python.error"))
          }
        }
      }
    }
  }
}

suspend fun ErrorSink.emit(@NlsSafe message: String) {
  emit(PyError.Message(message))
}

suspend fun ErrorSink.emit(e: PyExecutionException) {
  emit(PyError.ExecException(e))
}

fun failure(message: @Nls String): Failure<PyError.Message> = Result.failure(PyError.Message(message))
fun failure(failure: PyExecutionFailure): Failure<PyError.ExecException> = Result.failure(PyError.ExecException(failure))

@Deprecated("Migrate to native python result")
fun <T> kotlin.Result<T>.asPythonResult(): Result<T, PyError> =
  Result.success(getOrElse {
    return if (it is PyExecutionException) {
      failure(it)
    }
    else {
      failure(it.localizedMessage)
    }
  }
  )

@Deprecated("Use python result, not kotlin result")
fun <S, E> Result<S, E>.asKotlinResult(): kotlin.Result<S> = when (this) {
  is Failure -> kotlin.Result.failure(
    when (val r = error) {
      is Throwable -> r
      is PyError.Message -> Exception(r.message)
      is PyError.ExecException -> Exception(r.execFailure.userMessage)
      else -> Exception(r.toString())
    }
  )
  is Success -> kotlin.Result.success(result)
}


