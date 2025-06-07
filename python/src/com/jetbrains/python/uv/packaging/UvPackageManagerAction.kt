// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.uv.packaging

import com.intellij.openapi.actionSystem.AnActionEvent
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.management.PythonPackageManagerAction
import com.jetbrains.python.packaging.management.getPythonPackageManager
import com.jetbrains.python.sdk.uv.UvPackageManager

internal sealed class UvPackageManagerAction : PythonPackageManagerAction<UvPackageManager, String>() {
  override fun getManager(e: AnActionEvent): UvPackageManager? = e.getPythonPackageManager()
}

internal class UvSyncAction() : UvPackageManagerAction() {
  override suspend fun execute(e: AnActionEvent, manager: UvPackageManager): PyResult<String> {
    return manager.sync()
  }
}

internal class UvLockAction() : UvPackageManagerAction() {
  override suspend fun execute(e: AnActionEvent, manager: UvPackageManager): PyResult<String> {
    return manager.lock()
  }
}