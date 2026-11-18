// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

/*
 * The test support of [PythonInterpreterRegistry]: mock SDKs and SDKs that a test makes itself. Production code does not
 * hand an [Sdk] to the registry.
 */

/**
 * Adds a prebuilt mock [sdk] to [pyProject] for a test. A mock SDK has no real interpreter, so
 * [PythonInterpreterRegistry.registerPythonInterpreter] cannot create it. It associates [sdk] with [pyProject]. Remove it
 * with [PythonInterpreterRegistry.removePythonInterpreter].
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
  val interpreter = addSdk(sdk)
  // The legacy flow keeps the SDK in the SDK table, which outlives the project. It goes with the project, as an SDK of
  // the interpreter library flow does.
  if (!isInterpreterLibraryFlowEnabled()) {
    Disposer.register(PythonInterpreterSdks.getInstance(project)) {
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
  holdSdk(key, sdk)
  awaitInterpreter(sdk)
  return key
}

/**
 * Adds the SDK of [interpreter] to the SDK table for an old test that has no [PyProject], such as a JUnit4 test that
 * sets the SDK on its module through the platform. Production code registers only through
 * `PyProject.setPythonInterpreter`. Remove it with [removeTestInterpreter].
 */
@TestOnly
suspend fun PythonInterpreterRegistry.addTestInterpreter(interpreter: PythonInterpreter): PythonInterpreter = addSdk(interpreter.sdk)

/** Removes an [interpreter] that [addTestInterpreter] added. Returns when the registry no longer holds it. */
@TestOnly
suspend fun PythonInterpreterRegistry.removeTestInterpreter(interpreter: PythonInterpreter) {
  removeSdk(interpreter.sdk)
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
    if (sdks.keyOf(sdk) != null) PythonInterpreterRegistry.getInstance(project).removeSdk(sdk)
  }
}

/** Removes [interpreter] for a test that holds the interpreter that a flow created, see [removeTestSdkFromOpenProjects]. */
@TestOnly
suspend fun removeTestInterpreterFromOpenProjects(interpreter: PythonInterpreter) {
  removeTestSdkFromOpenProjects(interpreter.sdk)
}
