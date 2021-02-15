// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python

import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

/**
 * Operation result to be used with pattern matching.
 * Must be replaced with stdlib solution after [https://github.com/Kotlin/KEEP/blob/master/proposals/stdlib/result.md] completion.
 *
 * Can't be moved to core module because core modules do not support Kotlin and there is no sealed classes in java.
 */
sealed class Result<SUCC, ERR> {
  data class Failure<SUCC, ERR>(val error: ERR) : Result<SUCC, ERR>()
  data class Success<SUCC, ERR>(val result: SUCC) : Result<SUCC, ERR>()

  fun <RES> map(map: (SUCC) -> RES): Result<RES, ERR> =
    when (this) {
      is Success -> Success(map(result))
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