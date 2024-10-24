// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python

/**
 * Operation result to be used with pattern matching.
 * Unlike Kotlin `Result`, [ERR] could be anything (See [LocalizedErrorString])
 */
sealed class Result<SUCC, ERR> {
  data class Failure<SUCC, ERR>(val error: ERR) : Result<SUCC, ERR>()
  data class Success<SUCC, ERR>(val result: SUCC) : Result<SUCC, ERR>()

  fun <RES> map(map: (SUCC) -> RES): Result<RES, ERR> =
    when (this) {
      is Success -> Success(map(result))
      is Failure -> Failure(error)
    }

  /**
   * Maps success result to another one with same error
   * ```kotlin
   * findBeer().mapResult{openBeer(it)}.mapResult{drinkIt(it)}
   * ```
   */
  inline fun <NEW_S> mapResult(map: (SUCC) -> Result<NEW_S, ERR>): Result<NEW_S, ERR> =
    when (this) {
      is Success -> map(result)
      is Failure -> Failure(error)
    }

  val successOrNull: SUCC? get() = if (this is Success) result else null
  fun orThrow(onError: (ERR) -> Throwable = { e -> AssertionError(e) }): SUCC {
    when (this) {
      is Success -> return result
      is Failure -> throw onError(this.error)
    }
  }
}