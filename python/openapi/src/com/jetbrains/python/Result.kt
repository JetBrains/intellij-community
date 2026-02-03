// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.openapi.diagnostic.Logger
import com.jetbrains.python.Result.Failure
import com.jetbrains.python.Result.Success
import com.jetbrains.python.errorProcessing.MessageError
import org.jetbrains.annotations.Nls

/**
 * TL;TR: This class is kinda low-level. Use [com.jetbrains.python.errorProcessing.PyResult] in upper-level signatures,
 * but use this class as low-level api (preferably internal) inside your modules.
 *
 * Operation result to be used as `Maybe` instead of checked exceptions.
 * Unlike Kotlin `Result`, [ERR] could be anything (i.e [String]).
 *
 * Warning:
 * This class is for *user* errors (IO errors like: no Internet conn., file provided by user is malformed, external process died).
 * Code bugs (NPE, CNFE, AOOBE e.t.c.) are *developer* errors, and must be represented as exceptions.
 * Do not wrap every single `Throwable` into this class.
 *
 * Typical usages:
 *
 * ```kotlin
 *  when(val r = someFun() {
 *   is Result.Success -> r.result // is ok
 *   is Result.Failure -> r.error // is error
 *  }
 * ```
 * Get result or throw error (I am 100% sure there is no error): [orThrow].
 *
 * Chain several calls, get latest result or first error (all errors are the same): [mapResult].
 *
 * When errors are different: [mapSuccessError]
 *
 * Fast return: [getOr]
 * ```kotlin
 * fun foo() {
 *  val data = getSomeResult().getOr { return }
 * }
 * ```
 *
 * Return from function with same error
 * ```kotlin
 * fun foo():Result<String, Int> {
 *  // Returns Result<Foo, Int>
 *  getSomeResult().getOr { return it }
 * }
 * ```
 * See showcase in tests.
 */
sealed class Result<out SUCC, out ERR> {
  data class Failure<out ERR>(val error: ERR) : Result<Nothing, ERR>()
  data class Success<out SUCC>(val result: SUCC) : Result<SUCC, Nothing>()

  /**
   * See also [mapSuccessError], [mapError]
   */
  fun <RES> mapSuccess(map: (SUCC) -> RES): Result<RES, ERR> =
    when (this) {
      is Success -> Success(map(result))
      is Failure -> Failure(error)
    }

  /***
   * ```kotlin
   *  val data = someFun().getOr { return }
   * ```
   */
  inline fun getOr(onFailure: (err: Failure<ERR>) -> Nothing): SUCC {
    when (this) {
      is Failure -> onFailure(this)
      is Success -> return result
    }
  }


  /**
   * Same as [mapResult] but for different errors
   * ```kotlin
   * val drinkResultOrFirstError = findBeer()
   * .mapResult{ openBeer(it) }
   * .mapResultWithErr(
   *   onSuccess = { drink(it) },
   *   onErr = { LocalizedErrorString("Oops, ${it.message}") }
   * )
   * ```
   * See also [mapError]
   */
  inline fun <NEW_ERR, NEW_S> mapSuccessError(
    onSuccess: (SUCC) -> Result<NEW_S, NEW_ERR>,
    onErr: (ERR) -> NEW_ERR,
  ): Result<NEW_S, NEW_ERR> =
    when (this) {
      is Success -> onSuccess(result)
      is Failure -> Failure(onErr(error))
    }

  /**
   * Returns `null` if error, effectively *discarding* an error.
   * Use with care, only if:
   * 1. Error is insignificant (most errors are worth reporting to user!)
   * 2. You are sure this error has already been reported by other parts of the code
   */
  val successOrNull: SUCC? get() = if (this is Success) result else null
  val errorOrNull: ERR? get() = if (this is Failure) error else null


  /**
   * Like Rust `unwrap`: returns result or throws an exception. Use when error is unexpected (you are 100% it simply can't happen).
   * I.e: you access a file which is a part of the bundle. Unexistence is a serious bug.
   * This function is also good for tests.
   */
  fun orThrow(onError: (ERR) -> Throwable = { e -> if (e is Throwable) e else AssertionError(e) }): SUCC {
    when (this) {
      is Success -> return result
      is Failure -> throw onError(this.error)
    }
  }

  // To be backward compatible with Kotlin result
  companion object {
    fun <S> success(value: S): Success<S> = Success(value)
    fun <E> failure(error: E): Failure<E> = Failure(error)
    fun localizedError(message: @Nls String): Failure<MessageError> = failure(MessageError(message))
  }
}


/**
 * Maps success result to another one with same error
 * ```kotlin
 * val drinkResultOrFirstError = findBeer()
 * .mapResult{ openBeer(it) }
 * .mapResult{ drinkIt(it) }
 * ```
 */
fun <SUCC, NEW_S, ERR> Result<SUCC, ERR>.mapResult(map: (SUCC) -> Result<NEW_S, ERR>): Result<NEW_S, ERR> =
  when (this) {
    is Success -> map(result)
    is Failure -> this
  }

/**
 * Log an error to [logger] or return a result.
 * [logAsError] is rarely needed flag to log using `ERR` level (`WARN` is used otherwise).
 *
 * Do you need this method?
 * Can you show this error to a user?
 * If no (i.e. your process is in the background or does some bulk processing) then use this method.
 *
 * [logAsError] or not?
 * The default (`WARN`) level is for "rainy day", but still fully supported scenarios.
 * `WARN` logs are usually read by support engineers in their attempts to find user environment problem or misconfiguration.
 * Think: No Internet connection, external process died unexpectedly, file can't be accessed, provided `pyproject.toml` is malformed.
 *
 * `ERR` mode is reported to the exception analyzer, and it is almost always a *developer* problem. This is an issue *we* must fix.
 * In most cases exceptions/errors are the best tools for such cases, but sometimes we can __live with this bug for some time__ i.e:
 * IO access from EDT. It can also be used in tests.
 */
fun <T> Result<T, *>.orLogException(logger: Logger, logAsError: Boolean = false): T? = orLogExceptionImpl(logger, asWarn = !logAsError)

private fun <T> Result<T, *>.orLogExceptionImpl(logger: Logger, asWarn: Boolean): T? =
  when (val r = this) {
    is Failure -> {
      when (val err = r.error) {
        is Throwable -> {
          if (asWarn) logger.warn(err) else logger.error(err)
        }
        else -> {
          if (asWarn) logger.warn(err.toString()) else logger.error(err.toString())
        }
      }
      null
    }
    is Success -> r.result
  }

inline fun <S, E> Result<S, E>.onSuccess(code: (S) -> Unit): Result<S, E> {
  when (this) {
    is Failure -> Unit
    is Success -> {
      code(this.result)
    }
  }
  return this
}

inline fun <S, E> Result<S, E>.onFailure(code: (E) -> Unit): Result<S, E> {
  when (this) {
    is Success -> Unit
    is Failure -> {
      code(this.error)
    }
  }
  return this
}

/**
 * Like [Result.mapSuccess] but for error. See also [Result.mapSuccessError]
 */
inline fun <S, E, E2> Result<S, E>.mapError(code: (E) -> E2): Result<S, E2> =
  when (this) {
    is Success -> this
    is Failure -> {
      Result.failure(code(this.error))
    }
  }

// aliases to drop-in replace for kotlin Result

/**
 * Read [Result.successOrNull] first!
 */
fun <S, E> Result<S, E>.getOrNull(): S? = this.successOrNull
val <S, E> Result<S, E>.isFailure: Boolean get() = this is Failure
val <S, E> Result<S, E>.isSuccess: Boolean get() = this is Success

/**
 * Read [Result.orThrow] first!
 */
fun <S, E> Result<S, E>.getOrThrow(): S = orThrow()


/**
 * See [Result.mapSuccess]
 */
inline fun <S, E, E2> Iterable<Result<S, E>>.mapError(code: (E) -> E2): List<Result<S, E2>> =
  map { it.mapError(code) }
