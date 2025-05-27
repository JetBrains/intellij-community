// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.python.errorProcessing.PyResult

internal object PythonPackageManagerUIHelpers {
  suspend fun <T> runPackagingOperationBackground(
    project: Project,
    @ProgressTitle title: String,
    operation: suspend (() -> PyResult<T>),
  ): PyResult<T> = withBackgroundProgress(project = project, title, cancellable = true) {
    operation()
  }
}

