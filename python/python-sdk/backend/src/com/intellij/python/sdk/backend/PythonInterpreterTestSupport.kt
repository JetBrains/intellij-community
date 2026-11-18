// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.notifyPythonSdkChanged
import com.jetbrains.python.sdk.writePythonSdk
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

/*
 * The test support of the interpreters: mock SDKs, SDKs that a test makes itself, and the interpreter of a module of a
 * test. Production code does not hand an [Sdk] to the registry, and sets an interpreter only with
 * `PyProject.setPythonInterpreter`.
 */

/**
 * Adds a prebuilt mock [sdk] to [pyProject] for a test. A mock SDK has no real interpreter, so
 * [PythonInterpreterRegistry.register] cannot create it. It associates [sdk] with [pyProject]. Remove it
 * with [PythonInterpreterRegistry.remove].
 */
@TestOnly
suspend fun PythonInterpreterRegistry.addMockPythonInterpreter(pyProject: PyProject, sdk: Sdk): PythonInterpreter =
  addMockPythonInterpreter(pyProject.baseDir, sdk)

/**
 * Adds a prebuilt mock [sdk] owned by [ownerDir] for a test, see [PythonInterpreter.ownerDir]. A test that has no
 * [PyProject] of its own passes the base directory of the project, so it gets no extra module. Remove it with
 * [removeTestInterpreter].
 */
@TestOnly
suspend fun PythonInterpreterRegistry.addMockPythonInterpreter(ownerDir: Path, sdk: Sdk): PythonInterpreter {
  // A commit, not a change in place: the modificator of a later commit copies the data of the last commit.
  edtWriteAction {
    val modificator = sdk.sdkModificator
    (modificator.sdkAdditionalData as? PythonSdkAdditionalData)?.associatedModulePath = ownerDir.toString()
    modificator.commitChanges()
  }
  val interpreter = store.addSdkForTests(sdk)
  // The legacy flow keeps the SDK in the SDK table, which outlives the project. It goes with the project, as an SDK of
  // the interpreter library flow does.
  if (!isInterpreterLibraryFlowEnabled()) {
    Disposer.register(PythonInterpreterSdks.getInstance(store.project)) {
      WriteAction.run<RuntimeException> {
        if (ProjectJdkTable.getInstance().allJdks.any { it === sdk }) ProjectJdkTable.getInstance().removeJdk(sdk)
      }
    }
  }
  return interpreter
}

/**
 * Holds [sdk] itself, owned by [ownerDir], for a test that makes its SDK itself, see `Module.setPythonSdkForTests`.
 * The test keeps the [Sdk] object it made and edits its roots, so the registry holds that object and does not build
 * one. Returns the key of the interpreter, or `null` outside the interpreter library flow, or for an SDK that names no
 * interpreter.
 */
@TestOnly
suspend fun PythonInterpreterRegistry.putTestSdk(sdk: Sdk, ownerDir: Path): PythonInterpreterKey? {
  if (!isInterpreterLibraryFlowEnabled()) return null
  edtWriteAction {
    val modificator = sdk.sdkModificator
    (modificator.sdkAdditionalData as? PythonSdkAdditionalData)?.associatedModulePath = ownerDir.toString()
    modificator.commitChanges()
  }
  val key = sdk.pythonInterpreterResultAsync().successOrNull?.key ?: return null
  store.holdSdkForTests(key, sdk)
  return key
}

/**
 * Adds the SDK of [interpreter] to the SDK table for an old test that has no [PyProject], such as a JUnit4 test that
 * sets the SDK on its module through the platform. Production code registers only through
 * `PyProject.setPythonInterpreter`. Remove it with [removeTestInterpreter].
 */
@TestOnly
suspend fun PythonInterpreterRegistry.addTestInterpreter(interpreter: PythonInterpreter): PythonInterpreter = store.addForTests(interpreter)

/** Removes an [interpreter] that [addTestInterpreter] added. Returns when the registry no longer holds it. */
@TestOnly
suspend fun PythonInterpreterRegistry.removeTestInterpreter(interpreter: PythonInterpreter) {
  store.remove(interpreter)
}

/**
 * Removes [sdk] for a test that holds only the SDK, such as the SDK a flow created. In the interpreter library flow it
 * leaves each open project that holds it. Otherwise it leaves the SDK table.
 */
@TestOnly
suspend fun removeTestSdkFromOpenProjects(sdk: Sdk) {
  if (!isInterpreterLibraryFlowEnabled()) {
    edtWriteAction { ProjectJdkTable.getInstance().removeJdk(sdk) }
    return
  }
  for (project in ProjectManager.getInstance().openProjects) {
    val sdks = project.serviceIfCreated<PythonInterpreterSdks>() ?: continue
    if (sdks.keyOf(sdk) != null) PythonInterpreterRegistry.getInstance(project).store.removeSdkForTests(sdk)
  }
}

/** Removes [interpreter] for a test that holds the interpreter that a flow created, see [removeTestSdkFromOpenProjects]. */
@TestOnly
suspend fun removeTestInterpreterFromOpenProjects(interpreter: PythonInterpreter) {
  removeTestSdkFromOpenProjects(interpreter.sdk)
}

/**
 * Makes [sdk] the interpreter of this module, for a test that makes its SDK itself, such as a mock SDK or an SDK of a
 * real environment. Every other caller uses `PyProject.setPythonInterpreter`.
 *
 * In the interpreter library flow, see [isInterpreterLibraryFlowEnabled], the registry holds [sdk] itself, owned by the
 * first content root of the module, see [putTestSdk], and the SDK is in no SDK table. The
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
  val name = key?.let { PyInterpreterLibraryName.of(checkNotNull(project.interpreterOwnerProjectDir), it) }
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
  blockingForTests { PythonInterpreterRegistry.getInstance(project).store.removeSdkForTests(sdk) }
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

/** The last computation of the interpreter list against the records, for the report of a test wait that does not end. */
@TestOnly
fun PythonInterpreterRegistry.describeForTests(): String = store.describeForTests()
