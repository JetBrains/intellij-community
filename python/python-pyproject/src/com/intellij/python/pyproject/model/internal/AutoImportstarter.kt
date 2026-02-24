package com.intellij.python.pyproject.model.internal

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.python.pyproject.model.PyProjectModelSettings
import com.intellij.python.pyproject.model.internal.autoImportBridge.PyProjectAutoImportService
import com.intellij.python.pyproject.model.internal.platformBridge.startVenvExclusion
import org.jetbrains.annotations.ApiStatus


/**
 * Starts autoimport process if [enabled] or simply "skips" to the next step: [notifyModelRebuilt].
 * This method usually called by [com.intellij.python.pyproject.model.internal.platformBridge.PyProjectSyncActivity] except for new projects.
 * In this case, it is postponed till project generation (see usages).
 *
 * This method can only be called once (see [PyProjectAutoImportService.start])
 */
@ApiStatus.Internal
suspend fun startAutoImportIfNeeded(project: Project) {
  startVenvExclusion(project)
  if (PyProjectModelSettings.isFeatureEnabled) {
    project.service<PyProjectAutoImportService>().start()
  }
  else {
    // User disabled "pyproject.toml -> module" conversion (aka project model rebuilding), but we still need to notify listener,
    // so they configure SDK
    notifyModelRebuilt(project)
  }
}