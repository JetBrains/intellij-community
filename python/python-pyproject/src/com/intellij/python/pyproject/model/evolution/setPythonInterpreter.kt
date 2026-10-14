// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.evolution

import com.intellij.ide.projectView.actions.MarkRootsManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.python.sdk.backend.NativePythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.sdk.backend.TargetPythonInterpreter
import com.intellij.python.sdk.backend.evolution.EvoWorkspace
import com.intellij.python.sdk.backend.getSdkAPI
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.project
import com.jetbrains.python.sdk.writePythonSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * Makes [interpreter] the interpreter of this project, and returns the interpreter that it then has. It sets the SDK of
 * the module and excludes a virtual environment inside the module. A `null` [interpreter] removes the interpreter.
 *
 * It is the one way to set an interpreter, and the one way to add one to the registry, see
 * [PythonInterpreterRegistry.registerPythonInterpreter]. [interpreter] does not have to belong to this project, see
 * [PythonInterpreter.ownerDir]. A member of a tool workspace uses the interpreter of the workspace root, a module can use
 * the interpreter of its parent, and an importer such as Bazel sets one interpreter on many modules. Tests that cannot
 * use it call `Module.setPythonSdkForTests`.
 *
 * With [waitForSnapshot], it returns only when the snapshot holds [interpreter] for this project. Then a caller that
 * reads the snapshot next sees the new interpreter. Pass `false` when nothing reads the snapshot after the call, such
 * as a UI action.
 */
@ApiStatus.Internal
suspend fun PyProject.setPythonInterpreter(interpreter: PythonInterpreter?, waitForSnapshot: Boolean = true): PyResult<PythonInterpreter?> {
  val set = interpreter?.let { PythonInterpreterRegistry.getInstance(project).registerPythonInterpreter(it) }
  @Suppress("DEPRECATION") // The module and the project store an SDK.
  val sdk = set?.getSdkAPI()
  val module = residesOnModule
  module.writePythonSdk(sdk)
  set?.let { excludeInnerVirtualEnv(it) }
  if (waitForSnapshot) EvoPyProjectModel.getInstance(project).awaitInterpreterOf(listOf(this))
  return PyResult.success(set)
}

/**
 * Makes [interpreter] the interpreter of every member of this tool workspace, and returns the interpreter they then
 * have. The root sets it first, so it registers the interpreter it owns. Each other member then uses that one.
 */
@ApiStatus.Internal
suspend fun EvoWorkspace.setOnWorkspace(interpreter: PythonInterpreter): PyResult<PythonInterpreter> {
  val applied = root.pyProject.setPythonInterpreter(interpreter, waitForSnapshot = false).getOr { return it } ?: interpreter
  for (member in members) {
    if (member.pyProject == root.pyProject) continue
    member.pyProject.setPythonInterpreter(applied, waitForSnapshot = false).getOr { return it }
  }
  return PyResult.success(applied)
}

private suspend fun PyProject.excludeInnerVirtualEnv(interpreter: PythonInterpreter) {
  // Only a native interpreter has an environment root on this machine.
  val home = when (interpreter) {
    is NativePythonInterpreter -> interpreter.pythonHomePath
    is TargetPythonInterpreter -> null
  }
  val root = home?.let { VirtualFileManager.getInstance().findFileByNioPath(it) } ?: return
  // modifyRoots takes the write action itself.
  withContext(Dispatchers.EDT) {
    MarkRootsManager.modifyRoots(residesOnModule, arrayOf(root)) { vFile, entry ->
      entry.addExcludeFolder(vFile)
    }
  }
}
