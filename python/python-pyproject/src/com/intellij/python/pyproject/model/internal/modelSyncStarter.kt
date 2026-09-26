package com.intellij.python.pyproject.model.internal

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.pyproject.model.PyProjectModelSettings
import com.intellij.python.pyproject.model.internal.platformBridge.PyProjectModelSyncService
import com.intellij.python.pyproject.model.internal.platformBridge.startVenvExclusion
import org.jetbrains.annotations.ApiStatus


/**
 * Starts the model sync if [PyProjectModelSettings.featureStateInRegistry] allows it. Otherwise it simply
 * "skips" to the next step: [notifyModelRebuilt].
 * This method usually called by [com.intellij.python.pyproject.model.internal.platformBridge.PyProjectSyncActivity]
 * except for new projects. In this case, it is postponed till project generation (see usages).
 *
 * This method can only be called **once** (call [PyProjectModelSyncService.start] to enable/disable the sync)
 */
@ApiStatus.Internal
suspend fun startPyProjectModelSyncIfNeeded(project: Project, reasonForDebug: @NlsSafe String) {
  log.info("Model sync started $reasonForDebug")
  startVenvExclusion(project)
  askUserIfPyProjectMustBeEnabled(project)
  // Only start the sync if the feature is enabled (registry default + user choice).
  if (PyProjectModelSettings.getInstance(project).usePyprojectToml) {
    try {
      project.service<PyProjectModelSyncService>().start()
    }
    catch (e: Throwable) {
      log.error("model sync failed ($reasonForDebug)", e)
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
