// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.python.pyproject.model.evolution.evoPyProjects
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyUpdateProjectSdkAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    PyPackageCoroutine.launch(project) {
      for (evoPyProject in project.evoPyProjects()) {
        val interpreter = evoPyProject.interpreter
        if (interpreter == null) {
          LOG.info("Skipping ${evoPyProject.pyProject.baseDir}, because it has no interpreter")
          continue
        }
        PythonSdkUpdater.scheduleUpdate(interpreter, project)
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private val LOG = logger<PyUpdateProjectSdkAction>()
