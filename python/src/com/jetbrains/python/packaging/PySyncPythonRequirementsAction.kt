// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import kotlinx.coroutines.launch


internal class PySyncPythonRequirementsAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val module = e.getData(PlatformCoreDataKeys.MODULE) ?: return
    e.coroutineScope.launch { syncWithImports(module) }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}