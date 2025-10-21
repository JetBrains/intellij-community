// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.configuration.suppressors

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.jetbrains.python.inspections.requirement.RunningPackagingTasksListener

internal class PyPackageRequirementsInspectionSuppressor(module: Module) : Disposable {

  private val listener = RunningPackagingTasksListener(module)

  init {
    listener.started()
  }

  override fun dispose() {
    listener.finished(emptyList())
  }
}