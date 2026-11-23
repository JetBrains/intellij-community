package com.jetbrains.python.sdk

import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.module.PyModuleService
import org.jetbrains.annotations.ApiStatus
import com.intellij.python.sdk.backend.pythonInterpreterResult

/**
 * Writes the SDK of [interpreter] to this module as an older build reads it. This build never reads the value back: it reads the
 * interpreter library entry. The write only keeps the project usable in an older build. It tells
 * [PythonInterpreterListener] nothing: the caller does that, see [notifyPythonInterpreterChanged].
 *
 * Production code calls `PyProject.setPythonInterpreter`, which also writes the entry. Tests call [setPythonSdkForTests].
 *
 * Must be called under [withSdkConfigurationLock] to prevent concurrent Module/SDK changes.
 */
@ApiStatus.Internal
@RequiresBackgroundThread(generateAssertion = false)
fun Module.writePythonInterpreter(interpreter: PythonInterpreter?) {
  writePythonSdk(interpreter?.sdk)
}

/** [writePythonInterpreter] with the SDK of the interpreter, for the test support that holds only an SDK. */
@RequiresBackgroundThread(generateAssertion = false)
internal fun Module.writePythonSdk(newSdk: Sdk?) {
  thisLogger.info("Setting PythonSDK $newSdk to module $this")
  // Detects the environment again, so the interpreter of the new SDK shows its current version.
  newSdk?.pythonInterpreterResult(project, forceRefresh = true)
  // The daemon restarts when the snapshot holds the new interpreter. See EvoPyProjectModel.
  ApplicationManager.getApplication().invokeAndWait {
    WriteAction.runAndWait<Throwable> {
      PyModuleService.getInstance(project).setPythonSdk(this, newSdk)
    }
  }
}

/**
 * Tells [PythonInterpreterListener] that this module had [previous] and has [current] now. It runs whether or not the
 * SDK of the module is written, see [writePythonInterpreter].
 */
@ApiStatus.Internal
fun Module.notifyPythonInterpreterChanged(previous: PythonInterpreter?, current: PythonInterpreter?) {
  if (previous == current) return
  ApplicationManager.getApplication().messageBus.syncPublisher(PythonInterpreterListener.TOPIC).moduleInterpreterChanged(this, previous, current)
}

private val thisLogger = fileLogger()
