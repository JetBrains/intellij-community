package com.jetbrains.python.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.module.PyModuleService
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import com.intellij.python.sdk.backend.pythonInterpreter

/**
 * The SDK stored on this module, read when the project model has loaded. For the code that writes interpreters and
 * builds the project structure from them, such as the registry and the configuration lock, which need the stored value
 * at this instant. Every other caller asks the interpreter of its Python project, `PyProject.getInterpreter`.
 */
@ApiStatus.Internal
suspend fun Module.readPythonSdkForConfiguration(): Sdk? =
  PyModuleService.getInstance(getProject()).findPythonSdkWaitingForProjectModel(this)

/**
 * Sets [sdk] on this module, for a test whose SDK cannot become a `PythonInterpreter`, such as a mock SDK, or whose
 * module is not a Python project. Every other caller uses `PyProject.setPythonInterpreter`, which is the one way to set
 * an interpreter.
 */
@TestOnly
@ApiStatus.Internal
@RequiresBackgroundThread(generateAssertion = false)
fun Module.setPythonSdkForTests(sdk: Sdk?): Unit = writePythonSdk(sdk)

/**
 * Writes [newSdk] to this module and notifies [PySdkListener]. The one low-level writer of a module SDK.
 *
 * Production code calls `PyProject.setPythonInterpreter`, which also waits for the snapshot. Tests call
 * [setPythonSdkForTests].
 *
 * Must be called under [withSdkConfigurationLock] to prevent concurrent Module/SDK changes.
 */
@ApiStatus.Internal
@RequiresBackgroundThread(generateAssertion = false)
fun Module.writePythonSdk(newSdk: Sdk?) {
  val prevSdk = PythonSdkUtil.readStoredPythonSdk(this)
  thisLogger.info("Setting PythonSDK $newSdk to module $this")
  newSdk?.pythonInterpreter(forceRefresh = true)
  // The daemon restarts when the snapshot holds the new interpreter. See EvoPyProjectModel.
  ApplicationManager.getApplication().invokeAndWait {
    WriteAction.runAndWait<Throwable> {
      PyModuleService.getInstance(project).setPythonSdk(this, newSdk)
    }
  }
  ApplicationManager.getApplication().messageBus.syncPublisher(PySdkListener.TOPIC).moduleSdkUpdated(this, prevSdk, newSdk)
}

private val thisLogger = fileLogger()
