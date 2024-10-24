// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python

/**
 * Operation result to be used as `Maybe` instead of checked exceptions.
 * Unlike Kotlin `Result`, [ERR] could be anything (See [LocalizedErrorString]).
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
 * When errors are different: [mapResultWithErr]
 *
 * Fast return: [getOr]
 * ```kotlin
 * fun foo() {
 *  val data = getSomeResult().getOr { return }
 * }
 * ```
 *
 * Return from function with same error (see [convertErr])
 * ```kotlin
 * fun foo():Result<String, Int> {
 *  // Returns Result<Foo, Int>
 *  getSomeResult().getOr { return it.convertErr()}
 * }
 * ```
 * See showcase in tests.
 */
sealed class Result<SUCC, ERR> {
  data class Failure<SUCC, ERR>(val error: ERR) : Result<SUCC, ERR>()
  data class Success<SUCC, ERR>(val result: SUCC) : Result<SUCC, ERR>()

  fun <RES> map(map: (SUCC) -> RES): Result<RES, ERR> =
    when (this) {
      is Success -> Success(map(result))
      is Failure -> Failure(error)
    }

  /***
   * ```kotlin
   *  val data = someFun().getOr { return }
   * ```
   */
  inline fun getOr(onFailure: (err: Failure<*, ERR>) -> Nothing): SUCC {
    when (this) {
      is Failure -> onFailure(this)
      is Success -> return result
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
  inline fun <NEW_S> mapResult(map: (SUCC) -> Result<NEW_S, ERR>): Result<NEW_S, ERR> =
    when (this) {
      is Success -> map(result)
      is Failure -> Failure(error)
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
   */
  inline fun <NEW_ERR, NEW_S> mapResultWithErr(
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
  fun orThrow(onError: (ERR) -> Throwable = { e -> AssertionError(e) }): SUCC {
    when (this) {
      is Success -> return result
      is Failure -> throw onError(this.error)
    }
  }
}

/**
 * Converts [Result.Failure] to another [Result.Failure] with the same error but different success.
 * See class doc for example
 */
fun <S, E> Result.Failure<*, E>.convertErr(): Result.Failure<S, E> = Result.Failure<S, E>(error)