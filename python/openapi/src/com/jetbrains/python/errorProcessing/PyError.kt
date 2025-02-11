// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.errorProcessing

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.execution.PyExecutionFailure
import com.jetbrains.python.execution.userMessage
import com.jetbrains.python.packaging.PyExecutionException
import org.jetbrains.annotations.Nls

sealed class PyError(val message: @NlsSafe String) {
  /**
   * Some "business" error: just a message to be displayed to a user
   */
  open class Message(message: @NlsSafe String) : PyError(message)

  /**
   * Some process can't be executed. To be displayed specially.
   */
  open class ExecException(val execFailure: PyExecutionFailure) : PyError(execFailure.userMessage)

  override fun toString(): String = message
}

suspend fun ErrorSink.emit(@NlsSafe message: String) {
  emit(PyError.Message(message))
}

suspend fun ErrorSink.emit(e: PyExecutionException) {
  emit(PyError.ExecException(e))
}

@Deprecated("Migrate to native python result")
fun <T> Result<T>.asPythonResult(): com.jetbrains.python.Result<T, PyError> =
  com.jetbrains.python.Result.Companion.success(getOrElse {
    return if (it is PyExecutionException) {
      failure(it)
    }
    else {
      failure(it.localizedMessage)
    }
  }
  )

@Deprecated("Use python result, not kotlin result")
fun <S, E> com.jetbrains.python.Result<S, E>.asKotlinResult(): Result<S> = when (this) {
  is com.jetbrains.python.Result.Failure -> Result.failure(
    when (val r = error) {
      is Throwable -> r
      is PyError.Message -> Exception(r.message)
      is PyError.ExecException -> Exception(r.execFailure.userMessage)
      else -> Exception(r.toString())
    }
  )
  is com.jetbrains.python.Result.Success -> Result.success(result)
}

fun failure(message: @Nls String): com.jetbrains.python.Result.Failure<PyError.Message> = com.jetbrains.python.Result.Companion.failure(PyError.Message(message))
fun failure(failure: PyExecutionFailure): com.jetbrains.python.Result.Failure<PyError.ExecException> = com.jetbrains.python.Result.failure(PyError.ExecException(failure))