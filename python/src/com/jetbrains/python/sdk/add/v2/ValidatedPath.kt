// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.util.NlsSafe
import com.intellij.python.pytools.backend.Version
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.add.v2.PathHolderAndValidationResult.Error
import com.jetbrains.python.sdk.add.v2.PathHolderAndValidationResult.ErrorWithPath
import com.jetbrains.python.sdk.add.v2.PathHolderAndValidationResult.Success


/**
 * There are 2 types of validated path:
 * * [Executable]
 * * [Folder]
 *
 * Each type is in one of three states (see [PathHolderAndValidationResult]):
 *
 * * [Success]: the path is valid. There is a path [P] and additional data [T]. For [Executable], [T] is [Version].
 * * [ErrorWithPath]: there is a path [P], but the validation failed. There is no [T], only a [PyError].
 * * [Error]: there is no path [P], only a [PyError].
 *
 * This class expresses these states statically.
 */
sealed class ValidatedPath<T, P : PathHolder>(private val userReadableValidationResultImpl: (T) -> @NlsSafe String?) {
  internal abstract val pathHolderAndValidationResult: PathHolderAndValidationResult<T, P>

  /**
   * The validation data [T] as a text for the user, for example the tool version.
   * Success with `null` if [T] has no text (for [Folder]).
   * Failure if the state is not [Success].
   */
  val userReadableValidationResult: PyResult<@NlsSafe String?>
    get() = validationResult.mapSuccess { userReadableValidationResultImpl(it.validationInfo) }

  data class Folder<P : PathHolder>(
    override val pathHolderAndValidationResult: PathHolderAndValidationResult<Unit, P>,
  ) : ValidatedPath<Unit, P>({ null })

  data class Executable<P : PathHolder>(
    override val pathHolderAndValidationResult: PathHolderAndValidationResult<Version, P>,
  ) : ValidatedPath<Version, P>({ it.value })
}

/**
 * See [ValidatedPath]
 */
sealed class PathHolderAndValidationResult<T, P : PathHolder> {
  /**
   * There is no [T] and no [P], only [error]
   */
  internal data class Error<T, P : PathHolder>(override val error: PyError) : PathHolderAndValidationResult<T, P>(), PathHolderResultWithError

  /**
   * [P] exists, but [T] is not available because of [error]
   */
  internal data class ErrorWithPath<T, P : PathHolder>(override val error: PyError, override val pathHolder: P) :
    PathHolderAndValidationResult<T, P>(),
    PathHolderResultWithError,
    PathHolderResultWithPath<P>

  /**
   * [P] and [T] exist
   */
  data class Success<T, P : PathHolder>(val validationInfo: T, override val pathHolder: P) :
    PathHolderAndValidationResult<T, P>(),
    PathHolderResultWithPath<P>
}

/**
 * See [ValidatedPath]
 * Returns [Success] (both [T] and [P]) only if the state is [Success], error otherwise
 */
internal val <T, P : PathHolder>ValidatedPath<T, P>.validationResult: PyResult<Success<T, P>>
  get() = when (val result = pathHolderAndValidationResult) {
    is Error, is ErrorWithPath -> PyResult.failure(result.error)
    is Success -> PyResult.success(result)
  }

/**
 * See [ValidatedPath]
 * Returns [T] only if the state is [Success], `null` otherwise
 */
internal val <T>ValidatedPath<T, *>.successOrNull: T? get() = validationResult.successOrNull?.validationInfo

/**
 * See [ValidatedPath]
 * Returns the error if [T] is not available ([Error] or [ErrorWithPath])
 */
internal val <T>ValidatedPath<T, *>.errorOrNull: PyError? get() = validationResult.errorOrNull


/**
 * Returns the path ([P]) if it exists ([Success] or [ErrorWithPath]), error otherwise
 */
internal val <T, P : PathHolder>ValidatedPath<T, P>.pathHolder: PyResult<P>
  get() = when (val result = pathHolderAndValidationResult) {
    is Error -> PyResult.failure(result.error)
    is ErrorWithPath, is Success -> PyResult.success(result.pathHolder)
  }


// Implementation details to simplify pattern matching
internal sealed interface PathHolderResultWithError {
  val error: PyError
}

internal sealed interface PathHolderResultWithPath<P> {
  val pathHolder: P
}
