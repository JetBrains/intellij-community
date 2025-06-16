// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.errorProcessing

import com.jetbrains.python.packaging.PyExecutionException
import org.jetbrains.annotations.Nls

/**
 * This class is expected to be used as a return value of most PyCharm APIs.
 * Use it instead of exceptions and Kotlin Result.
 * If your function returns [ExecError] only, use [PyExecResult]
 */
typealias PyResult<T> = com.jetbrains.python.Result<T, PyError>
/**
 * Like [PyResult] but error is always [ExecError]
 */
typealias PyExecResult<T> = com.jetbrains.python.Result<T, ExecError>

inline fun <reified T : PyError> failure(pyError: T): com.jetbrains.python.Result.Failure<T> = com.jetbrains.python.Result.failure(pyError)


@Deprecated("Migrate to native python result")
fun <T> Result<T>.asPythonResult(): com.jetbrains.python.Result<T, PyError> =
  com.jetbrains.python.Result.success(getOrElse {
    return if (it is PyExecutionException) {
      failure(it.pyError)
    }
    else {
      failure(MessageError(it.localizedMessage))
    }
  }
  )

@Deprecated("Use python result, not kotlin result")
fun <S> PyResult<S>.asKotlinResult(): Result<S> = when (this) {
  is com.jetbrains.python.Result.Success -> {
    Result.success(result)
  }
  is com.jetbrains.python.Result.Failure -> Result.failure(PyExecutionException(error))
}

/***
 * When returning error one level up, you must add description on what has happened, i.e:
 * ```kotlin
 * fun createUser(): PyResult<User> {
 *   val data = readFromJson().getOr("failed to read from json file $file") { return it }
 *   return User.parse(data)
 *  }
 *
 *  fun launchRocket():PyResult<Rocket> {
 *    val pilot = createUser().getOr("Could not get pilot"){return it}
 *  }
 * ```
 * This will provide readable chain to user: Can't get pilot: Can't create user: Can't read file.
 */
inline fun <SUCC, ERR : PyError> com.jetbrains.python.Result<SUCC, ERR>.getOr(actionFailed: @Nls String, onFailure: (err: com.jetbrains.python.Result.Failure<ERR>) -> Nothing): SUCC {
  when (this) {
    is com.jetbrains.python.Result.Failure -> {
      error.addMessage(actionFailed)
      onFailure(this)
    }
    is com.jetbrains.python.Result.Success -> return result
  }
}