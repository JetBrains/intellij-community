// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.application.EDT
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.ui.validation.CHECK_NON_EMPTY
import com.intellij.openapi.ui.validation.CHECK_NO_RESERVED_WORDS
import com.jetbrains.python.PyBundle
import com.jetbrains.python.Result.Failure
import com.jetbrains.python.Result.Success
import com.jetbrains.python.TraceContext
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.add.v2.PathHolderAndValidationResult.Error
import com.jetbrains.python.sdk.add.v2.PathHolderAndValidationResult.ErrorWithPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FolderValidator<P : PathHolder>(
  val fileSystem: FileSystem<P>,
  override val backProperty: ObservableMutableProperty<ValidatedPath.Folder<P>?>,
  propertyGraph: PropertyGraph,
  val defaultPathSupplier: suspend () -> PyResult<P>,
  val pathValidator: suspend (P) -> PyResult<Unit>,
) : PathValidator<Unit, P, ValidatedPath.Folder<P>> {
  override val isDirtyValue: ObservableMutableProperty<Boolean> = propertyGraph.property(true)
  override val isValidationInProgress: Boolean
    get() = validationJob.isActive

  lateinit var scope: CoroutineScope
  private lateinit var validationJob: Deferred<Unit>

  fun initialize(scope: CoroutineScope) {
    this.scope = scope
    this.validationJob = autodetectFolderPath()
  }

  suspend fun autodetectFolder() {
    validationJob.cancelAndJoin()
    validationJob = autodetectFolderPath()
  }


  private fun autodetectFolderPath(): Deferred<Unit> {
    return scope.async(TraceContext(PyBundle.message("python.sdk.validating.environment"), scope)) {
      withContext(Dispatchers.EDT) { isDirtyValue.set(true) }
      val pathResult = defaultPathSupplier.invoke()
      val path = runValidation(pathResult)
      withContext(Dispatchers.EDT) { backProperty.set(path) }
    }.apply {
      invokeOnCompletion {
        isDirtyValue.set(false)
      }
    }
  }

  private suspend fun runValidation(pathResult: PyResult<P>): ValidatedPath.Folder<P> {
    return when (pathResult) {
      is Failure -> ValidatedPath.Folder(Error(pathResult.error))
      is Success -> {
        val path = pathResult.result
        val result: PathHolderAndValidationResult<Unit, P> = when (val pathValidator = pathValidator(path)) {
          is Failure -> ErrorWithPath(pathValidator.error, path)
          is Success -> PathHolderAndValidationResult.Success(Unit, path)
        }
        ValidatedPath.Folder(result)
      }
    }
  }


  override fun validate(input: String) {
    scope.launch {
      if (input.isEmpty()) {
        autodetectFolderPath()
        return@launch
      }

      validationJob.cancelAndJoin()
      validationJob = scope.async {
        withContext(Dispatchers.EDT) { isDirtyValue.set(true) }

        val validatedFolderPath = withContext(Dispatchers.IO) {
          for (validator in arrayOf(CHECK_NON_EMPTY, CHECK_NO_RESERVED_WORDS)) {
            validator.curry { input }.validate()?.let {
              return@withContext ValidatedPath.Folder<P>(Error(MessageError(it.message)))
            }
          }

          val path = fileSystem.parsePath(input).getOr { error ->
            return@withContext ValidatedPath.Folder<P>(Error(error.error))
          }

          val result: PathHolderAndValidationResult<Unit, P> = when (val r = pathValidator.invoke(path)) {
            is Failure -> ErrorWithPath(r.error, path)
            is Success -> PathHolderAndValidationResult.Success(Unit, path)
          }
          ValidatedPath.Folder(result)
        }
        withContext(Dispatchers.EDT) { backProperty.set(validatedFolderPath) }
      }
      validationJob.invokeOnCompletion {
        isDirtyValue.set(false)
      }
    }
  }
}