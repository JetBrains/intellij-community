package com.intellij.python.pyproject.model.internal.platformBridge

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.python.pyproject.model.internal.autoImportBridge.PyProjectAutoImportService

internal class PyProjectSyncActivity : ProjectActivity {

  override suspend fun execute(project: Project) {
    if (project.isDefault) return // Service doesn't support default project
    project.service<PyProjectAutoImportService>().start()
  }
}