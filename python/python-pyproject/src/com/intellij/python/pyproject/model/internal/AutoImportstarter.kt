package com.intellij.python.pyproject.model.internal

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.pyproject.model.PyProjectModelSettings
import com.intellij.python.pyproject.model.internal.autoImportBridge.PyProjectAutoImportService
import com.intellij.python.pyproject.model.internal.platformBridge.startVenvExclusion
import org.jetbrains.annotations.ApiStatus


/**
 * Starts autoimport process if [PyProjectModelSettings.featureStateInRegistry] or simply "skips" to the next step: [notifyModelRebuilt].
 * This method usually called by [com.intellij.python.pyproject.model.internal.platformBridge.PyProjectSyncActivity] except for new projects.
 * In this case, it is postponed till project generation (see usages).
 *
 * This method can only be called **once** (call [PyProjectAutoImportService.start] to enable/disable import logic)
 */
@ApiStatus.Internal
suspend fun startAutoImportIfNeeded(project: Project, importReasonForDebug: @NlsSafe String) {
  log.info("Import started $importReasonForDebug")
  startVenvExclusion(project)
  askUserIfPyProjectMustBeEnabled(project)
  // Only start autoImport if the feature is enabled (registry default + user choice).
  if (PyProjectModelSettings.getInstance(project).usePyprojectToml) {
    try {
      project.service<PyProjectAutoImportService>().start()
    }
    catch (e: Throwable) {
      log.error("import failed ($importReasonForDebug)", e)
      throw e
    }
  }
  else {
    // User disabled "pyproject.toml -> module" conversion (aka project model rebuilding), but we still need to notify listener,
    // so they configure SDK
    notifyModelRebuilt(project)
  }
}

private val log = fileLogger()