package com.jetbrains.python.sdk

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.jetbrains.python.module.PyModuleService
import com.jetbrains.python.sdk.legacy.PythonSdkUtil

/**
 * Returns the Python SDK configured for this module, or `null` if none is set.
 *
 * Unlike [pythonSdk], this method suspends until the project model is fully loaded
 * before resolving the SDK, so it is safe to call during startup.
 */
suspend fun Module.findPythonSdk(): Sdk? {
  return PyModuleService.getInstance(getProject()).findPythonSdkWaitingForProjectModel(this)
}

/**
 * The Python SDK configured for this module.
 *
 * **Startup caveat:** the getter may return `null` when a Python SDK *is* configured but hasn't
 * resolved yet (e.g., the SDK table is still loading from a stale workspace model cache).
 * Prefer the suspended [findPythonSdk] extension in coroutine contexts.
 */
var Module.pythonSdk: Sdk?
  get() = PythonSdkUtil.findPythonSdk(this)
  set(newSdk) {
    val prevSdk = pythonSdk
    thisLogger.info("Setting PythonSDK $newSdk to module $this")
    ModuleRootModificationUtil.setModuleSdk(this, newSdk)
    runInEdt {
      DaemonCodeAnalyzer.getInstance(project).restart("Setting PythonSDK $newSdk to module $this")
    }
    ApplicationManager.getApplication().messageBus.syncPublisher(PySdkListener.TOPIC).moduleSdkUpdated(this, prevSdk, newSdk)
  }


private val thisLogger = fileLogger()
