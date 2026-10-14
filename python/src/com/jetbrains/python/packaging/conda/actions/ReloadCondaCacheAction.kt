// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.python.PyBundle
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.packaging.conda.CondaPackageCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class ReloadCondaCacheAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    ApplicationManager.getApplication().service<MyService>().coroutineScope.launch(Dispatchers.IO) {
      withBackgroundProgress(project, PyBundle.message("action.ReloadCondaPackageCacheAction.action.description")) {
        ApplicationManager.getApplication().service<CondaPackageCache>().fullRefetchAndReload().getOrThrow()
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

@Service
private class MyService(val coroutineScope: CoroutineScope)
