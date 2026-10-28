package com.jetbrains.python.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.module.PyModuleService
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import com.intellij.python.sdk.backend.pythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.python.sdk.backend.PyInterpreterLibraryName
import com.intellij.python.sdk.backend.PythonInterpreterKey
import com.intellij.python.sdk.backend.isInterpreterLibraryFlowEnabled
import com.intellij.python.sdk.backend.pyInterpreterDependency
import com.intellij.python.sdk.backend.setPyInterpreterDependency
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import java.nio.file.Path

/**
 * The Python SDK that an older build stored on this module, read when the project model has loaded. Only the registry
 * reads it, to give an owner to an SDK without one before the module is migrated to an interpreter library entry. Every
 * other caller asks the interpreter of its Python project, `PyProject.getInterpreter`.
 */
@ApiStatus.Internal
suspend fun Module.readPythonSdkForConfiguration(): Sdk? =
  PyModuleService.getInstance(getProject()).findPythonSdkWaitingForProjectModel(this)

/**
 * Makes [sdk] the interpreter of this module, for a test that makes its SDK itself, such as a mock SDK or an SDK of a
 * real environment. Every other caller uses `PyProject.setPythonInterpreter`.
 *
 * In the interpreter library flow, see [isInterpreterLibraryFlowEnabled], the registry holds [sdk] itself, owned by the
 * first content root of the module, see [PythonInterpreterRegistry.putTestSdk], and the SDK is in no SDK table. The
 * module gets the interpreter library entry of [sdk], as `PyProject.setPythonInterpreter` writes it. A test that then
 * edits the roots of [sdk] edits the SDK of the interpreter. Remove it with [removeTestSdk].
 *
 * In the legacy flow the module refers to [sdk] by name.
 */
@TestOnly
@ApiStatus.Internal
@RequiresBackgroundThread(generateAssertion = false)
fun Module.setPythonSdkForTests(sdk: Sdk?) {
  val prevSdk = PythonSdkUtil.readStoredPythonSdk(this)
  if (isInterpreterLibraryFlowEnabled()) {
    val ownerDir = ModuleRootManager.getInstance(this).contentRoots.minByOrNull { it.url }?.toNioPathOrNull()
    val key = if (sdk != null && ownerDir != null) blockingForTests { PythonInterpreterRegistry.getInstance(project).putTestSdk(sdk, ownerDir) } else null
    writeInterpreterLibraryEntryForTests(key)
  }
  else {
    writePythonSdk(sdk)
  }
  notifyPythonSdkChanged(prevSdk, sdk)
}

/** Writes the interpreter library entry of [key] to this module, or removes the entry for a `null` [key]. */
private fun Module.writeInterpreterLibraryEntryForTests(key: PythonInterpreterKey?) {
  val name = key?.let { PyInterpreterLibraryName.of(Path.of(checkNotNull(project.basePath)), it) }
  blockingForTests {
    project.workspaceModel.update("Set the Python interpreter of $name for a test") { storage ->
      val module = findModuleEntity(storage) ?: return@update
      if (module.dependencies.pyInterpreterDependency == null && name == null) return@update
      storage.modifyModuleEntity(module) { dependencies.setPyInterpreterDependency(name) }
    }
  }
}

/** Removes an SDK that [setPythonSdkForTests] gave this module from the store of the project, and disposes it. */
@TestOnly
@ApiStatus.Internal
fun Module.removeTestSdk(sdk: Sdk) {
  if (isDisposed || project.isDisposed) return
  blockingForTests { PythonInterpreterRegistry.getInstance(project).removeSdk(sdk) }
}

/**
 * Runs [action] and waits for it, on any thread. On the EDT it waits under a modal progress, which keeps the EDT
 * running, so [action] can take a write action there.
 */
private fun <T> Module.blockingForTests(action: suspend () -> T): T =
  if (ApplicationManager.getApplication().isDispatchThread) {
    runWithModalProgressBlocking(project, "") { action() }
  }
  else {
    runBlockingMaybeCancellable { action() }
  }

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
