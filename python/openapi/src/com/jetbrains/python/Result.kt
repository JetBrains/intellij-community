// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.openapi.diagnostic.Logger
import com.jetbrains.python.Result.Failure
import com.jetbrains.python.Result.Success

/**
 * TL;TR: This class is kinda low-level. Use [com.jetbrains.python.errorProcessing.PyResult] in upper-level signatures,
 * but use this class as low-level api (preferably internal) inside your modules.
 *
 * Operation result to be used as `Maybe` instead of checked exceptions.
 * Unlike Kotlin `Result`, [ERR] could be anything (i.e [String]).
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

  val successOrNull: SUCC? get() = if (this is Success) result else null


  /**
   * Like Rust `unwrap`: returns result or throws exception. Use when error is unexpected
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

fun <T> Result<T, *>.orLogException(logger: Logger): T? =
  when (val r = this) {
    is Failure -> {
      when (val err = r.error) {
        is Throwable -> {
          logger.error(err)
        }
        else -> {
          logger.error(err.toString())
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
fun <S, E> Result<S, E>.getOrNull(): S? = this.successOrNull
val <S, E> Result<S, E>.isFailure: Boolean get() = this is Failure
val <S, E> Result<S, E>.isSuccess: Boolean get() = this is Success
fun <S, E> Result<S, E>.exceptionOrNull(): S = this.orThrow()
fun <S, E> Result<S, E>.getOrThrow(): S = orThrow()

