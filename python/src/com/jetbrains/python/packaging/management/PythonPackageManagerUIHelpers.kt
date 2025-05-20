// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.util.ShowingMessageErrorSync

internal object PythonPackageManagerUIHelpers {
  suspend fun <T> runPackagingOperationBackground(
    project: Project,
    @ProgressTitle title: String,
    operation: suspend (() -> Result<T>),
  ): Result<T> = withBackgroundProgress(project = project, title, cancellable = true) {
    runPackagingOperationOrShowError {
      operation()
    }
  }

  private suspend fun <T> runPackagingOperationOrShowError(
    operation: suspend (() -> Result<T>),
  ): Result<T> {
    try {
      val result = operation()
      result.exceptionOrNull()?.let { throw it }
      return result
    }
    catch (ex: PyExecutionException) {
      ShowingMessageErrorSync.emit(ex.pyError)
      return Result.failure(ex)
    }
  }
}