package com.intellij.python.pyproject.model.internal.platformBridge

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.python.pyproject.model.internal.autoImportBridge.PyProjectAutoImportService
import com.intellij.python.pyproject.model.internal.notifyModelRebuilt

internal class PyProjectSyncActivity : ProjectActivity {
  private val enabled: Boolean get() = Registry.`is`("intellij.python.pyproject.model")

  override suspend fun execute(project: Project) {
    if (project.isDefault) return // Service doesn't support default project

    if (enabled) {
      project.service<PyProjectAutoImportService>().start()
    }
    else {
      // User disabled "pyproject.toml -> module" convertion (aka project model rebuilding), but we still need to notify listener,
      // so they configure SDK
      notifyModelRebuilt(project)
    }
  }
}