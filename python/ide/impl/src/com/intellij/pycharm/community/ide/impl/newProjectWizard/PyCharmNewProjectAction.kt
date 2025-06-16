// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.newProjectWizard;

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.ProjectGeneratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Service
private class PyCharmNewProjectActionCoroutineScopeHolder(@JvmField val coroutineScope: CoroutineScope)

/**
 * New project wizard entry point
 */
class PyCharmNewProjectAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    service<PyCharmNewProjectActionCoroutineScopeHolder>().coroutineScope.launch {
      runCatching {
        service<ProjectGeneratorManager>().initProjectGenerator(e.project)
      }
      withContext(Dispatchers.EDT) {
        val dlg = PyCharmNewProjectDialog()
        dlg.show()
      }
    }
  }
}
