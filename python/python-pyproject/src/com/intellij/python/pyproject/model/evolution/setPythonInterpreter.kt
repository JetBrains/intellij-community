// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.evolution

import com.jetbrains.python.sdk.notifyPythonInterpreterChanged
import java.nio.file.Path
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.python.sdk.backend.PythonInterpreterKey
import com.intellij.python.sdk.backend.resolveHomeEnvRef
import com.intellij.python.sdk.common.PyInterpreterRef
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.ide.projectView.actions.MarkRootsManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.python.sdk.backend.NativePythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.sdk.backend.isInterpreterLibraryFlowEnabled
import com.intellij.python.sdk.backend.TargetPythonInterpreter
import com.intellij.python.sdk.backend.evolution.EvoWorkspace
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.name
import com.jetbrains.python.project.project
import com.jetbrains.python.sdk.writePythonInterpreter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import com.intellij.python.sdk.backend.PyInterpreterLibraryName
import com.intellij.python.sdk.backend.interpreterOwnerProjectDir
import com.intellij.python.sdk.backend.setPyInterpreterDependency
import com.intellij.python.sdk.backend.pyInterpreterDependency

private val logger = fileLogger()

/**
 * Makes [interpreter] the interpreter of this project, and returns the interpreter that it then has. It writes the
 * interpreter library entry of the module (see [PyInterpreterLibraryName]), and excludes a virtual environment inside
 * the module. A `null` [interpreter] removes the interpreter.
 *
 * In the interpreter library flow, see [isInterpreterLibraryFlowEnabled], the entry is the only thing that names the
 * interpreter, and this build does not write the SDK reference of the module. In the legacy flow the SDK of the module
 * names the interpreter.
 *
 * It is the one way to set an interpreter, and the one way to add one to the registry, see
 * [PythonInterpreterRegistry.register]. [interpreter] does not have to belong to this project, see
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
  val set = interpreter?.let { PythonInterpreterRegistry.getInstance(project).register(it) }
  val libraryFlow = isInterpreterLibraryFlowEnabled()
  val name = if (libraryFlow) set?.let { libraryNameOf(it) } else null
  val previous = residesOnModule.readStoredInterpreter()
  // From the entry, so it is known also when the environment is gone and the interpreter does not resolve.
  val previousHome = residesOnModule.storedInterpreterKey()?.let(::homeOf)
  if (libraryFlow) {
    writeInterpreterLibraryEntry(name)
  }
  else {
    // An older build reads the SDK of the module.
    residesOnModule.writePythonInterpreter(set)
  }
  residesOnModule.notifyPythonInterpreterChanged(previous, set)
  previousHome?.let { removeGoneEnvExclusion(it) }
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

/** The name of the library of [interpreter] in this project. A project with a Python project has a directory. */
private fun PyProject.libraryNameOf(interpreter: PythonInterpreter): PyInterpreterLibraryName =
  libraryNameOf(checkNotNull(project.interpreterOwnerProjectDir) { "$project has no directory" }, interpreter)

/**
 * Writes the interpreter library entry of the module with [name], or removes it for `null`. It logs the entry before
 * and after, so a log shows which writer gave the module which interpreter.
 */
private suspend fun PyProject.writeInterpreterLibraryEntry(name: PyInterpreterLibraryName?) {
  project.workspaceModel.update("Set the Python interpreter of ${this.name}") { storage ->
    val module = residesOnModule.findModuleEntity(storage) ?: return@update
    val previous = module.dependencies.pyInterpreterDependency
    if (previous == null && name == null) return@update
    logger.info("Set the Python interpreter of ${this.name}: ${previous?.library?.name} -> $name")
    storage.modifyModuleEntity(module) { dependencies.setPyInterpreterDependency(name) }
  }
}

/** The home of a native environment that [key] places relative to its owner, or `null`, see [resolveHomeEnvRef]. */
private fun homeOf(key: PythonInterpreterKey): Path? = when (key.ref.mode) {
  PyInterpreterRef.Mode.Native -> resolveHomeEnvRef(key.ownerDir, key.ref.env.envRef)
  is PyInterpreterRef.Mode.Target -> null
}

/**
 * The home of the native environment that the interpreter library entry of [module] names, see [homeOf], or `null`.
 * [projectDir] is the directory of the project, see [interpreterOwnerProjectDir]. [excludeInnerVirtualEnv] excludes
 * this directory.
 */
internal fun interpreterEnvironmentHomeOf(module: ModuleEntity, projectDir: Path): Path? {
  val name = interpreterLibraryOf(module) ?: return null
  return homeOf(PythonInterpreterKey(name.ownerDir.resolveIn(projectDir), name.ref))
}

/**
 * Removes the exclusion of [home] from the module when the directory is gone. [excludeInnerVirtualEnv] excluded it for
 * an earlier interpreter. A directory that is there keeps its exclusion, so the files of an environment are not indexed
 * as the files of the project.
 */
private suspend fun PyProject.removeGoneEnvExclusion(home: Path) {
  if (withContext(Dispatchers.IO) { home.exists() }) return
  val url = VfsUtilCore.pathToUrl(home.invariantSeparatorsPathString)
  project.workspaceModel.update("Remove the exclusion of a gone Python environment of $name") { storage ->
    val module = residesOnModule.findModuleEntity(storage) ?: return@update
    module.contentRoots.flatMap { it.excludedUrls }.filter { it.url.url == url }.forEach(storage::removeEntity)
  }
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
