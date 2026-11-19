package com.jetbrains.python.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.module.PyModuleService
import org.jetbrains.annotations.ApiStatus
import com.intellij.python.sdk.backend.pythonInterpreter

/**
 * Writes [newSdk] to this module as an older build reads it. This build never reads the value back: it reads the
 * interpreter library entry. The write only keeps the project usable in an older build. It tells [PySdkListener]
 * nothing: the caller does that, see [notifyPythonSdkChanged].
 *
 * Production code calls `PyProject.setPythonInterpreter`, which also writes the entry. Tests call [setPythonSdkForTests].
 *
 * Must be called under [withSdkConfigurationLock] to prevent concurrent Module/SDK changes.
 */
@ApiStatus.Internal
@RequiresBackgroundThread(generateAssertion = false)
fun Module.writePythonSdk(newSdk: Sdk?) {
  thisLogger.info("Setting PythonSDK $newSdk to module $this")
  newSdk?.pythonInterpreter(forceRefresh = true)
  // The daemon restarts when the snapshot holds the new interpreter. See EvoPyProjectModel.
  ApplicationManager.getApplication().invokeAndWait {
    WriteAction.runAndWait<Throwable> {
      PyModuleService.getInstance(project).setPythonSdk(this, newSdk)
    }
  }
}

/**
 * Tells [PySdkListener] that this module had the interpreter of [prevSdk] and has the interpreter of [newSdk] now. It
 * runs whether or not the SDK of the module is written, see [writePythonSdk].
 */
@ApiStatus.Internal
fun Module.notifyPythonSdkChanged(prevSdk: Sdk?, newSdk: Sdk?) {
  if (prevSdk == newSdk) return
  ApplicationManager.getApplication().messageBus.syncPublisher(PySdkListener.TOPIC).moduleSdkUpdated(this, prevSdk, newSdk)
}

private val thisLogger = fileLogger()
