// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.newProjectWizard;

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.ProjectGeneratorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * New project wizard entry point
 */
class PyCharmNewProjectAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    currentThreadCoroutineScope().launch {
      runCatching {
        service<ProjectGeneratorManager>().initProjectGenerator(e.project)
      }.onFailure {
        thisLogger().warn("Failed to execute initProjectGenerator", it)
      }
      withContext(Dispatchers.EDT) {
        val dlg = PyCharmNewProjectDialog()
        dlg.show()
      }
    }
  }
}
