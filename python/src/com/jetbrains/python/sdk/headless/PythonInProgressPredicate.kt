// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.headless

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.observable.ActivityInProgressPredicate
import com.intellij.openapi.project.Project

class PythonInProgressPredicate : ActivityInProgressPredicate {

  override val presentableName: String = "python"

  override suspend fun isInProgress(project: Project): Boolean {
    return project.serviceAsync<PythonInProgressService>().isInProgress()
  }
}