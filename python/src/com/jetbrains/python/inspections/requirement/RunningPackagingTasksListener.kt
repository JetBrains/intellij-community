// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.requirement

import com.intellij.execution.ExecutionException
import com.intellij.openapi.module.Module
import com.jetbrains.python.packaging.PyPackageManagerUI
import com.jetbrains.python.packaging.management.PythonPackageManager

class RunningPackagingTasksListener(private val myModule: Module) : PyPackageManagerUI.Listener {
  override fun started() {
    setRunningPackagingTasks(myModule, true)
  }

  override fun finished(exceptions: List<ExecutionException>) {
    setRunningPackagingTasks(myModule, false)
  }

  private fun setRunningPackagingTasks(module: Module, value: Boolean) =
    module.putUserData(PythonPackageManager.RUNNING_PACKAGING_TASKS, value)
}