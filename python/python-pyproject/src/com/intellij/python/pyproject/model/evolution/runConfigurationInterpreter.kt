// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PyRunConfigurationInterpreter")

package com.intellij.python.pyproject.model.evolution

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.sdk.backend.binaryPathString
import com.intellij.python.sdk.backend.evolution.EvoPyProject
import com.intellij.python.sdk.backend.isInterpreterLibraryFlowEnabled
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import com.intellij.python.sdk.backend.PyInterpreterLibraryName

/**
 * The text a run configuration stores for [interpreter] in its `INTERPRETER` field, in the form of an interpreter
 * library name, see [PyInterpreterLibraryName]. The owner directory is relative to the project when a relative path
 * leads there, so the text then holds no local path. `null` for a project without a directory.
 */
@ApiStatus.Internal
fun interpreterTextOf(project: Project, interpreter: PythonInterpreter): String? {
  val projectDir = project.basePath?.let { Path.of(it) } ?: return null
  return libraryNameOf(projectDir, interpreter).toString()
}

/**
 * The interpreter that a run configuration of [project] names, read without the wait: `null` also before the registry has
 * its first list. [text] is the `INTERPRETER` field, see [interpreterTextOf]. A run configuration of an older build has
 * no such field. It has the legacy fields [sdkName] and [sdkHome] instead, and the interpreter is looked up from them.
 *
 * A home path alone is looked up among the interpreters of the workspace root of [module]: every member of a tool
 * workspace uses the interpreters of its root. A run configuration without a module uses the main Python project. When
 * the legacy fields name an interpreter that this build does not have, the interpreter library flow uses the interpreter
 * of [module].
 */
@ApiStatus.Internal
fun findRunConfigurationInterpreterIfReady(
  project: Project,
  module: Module?,
  text: String?,
  sdkName: String?,
  sdkHome: String?,
): PythonInterpreter? {
  val registry = PythonInterpreterRegistry.getInstance(project)
  if (!text.isNullOrEmpty()) {
    val name = PyInterpreterLibraryName.parse(text) ?: return null
    return project.keyOf(name)?.let { registry.resolveIfReady(it) }
  }
  val root = workspaceRootOf(project, module)
  if (!sdkName.isNullOrEmpty()) {
    registry.findByLegacySdkNameIfReady(sdkName, root?.pyProject?.baseDir)?.let { return it }
  }
  if (!sdkHome.isNullOrEmpty() && root != null) {
    registry.interpretersIfReady(root.pyProject).firstOrNull { FileUtil.pathsEqual(it.binaryPathString, sdkHome) }?.let { return it }
  }
  // An older build named an SDK that this build no longer has, such as one that only the SDK table of another machine
  // held. The interpreter library flow then runs the interpreter of the module, as the module names its own interpreter.
  if (isInterpreterLibraryFlowEnabled() && (!sdkName.isNullOrEmpty() || !sdkHome.isNullOrEmpty())) {
    return findModuleInterpreterIfReady(project, module)
  }
  return null
}

/** The interpreter of the Python project of [module], or of the main Python project without [module]. */
private fun findModuleInterpreterIfReady(project: Project, module: Module?): PythonInterpreter? {
  val snapshot = EvoPyProjectModel.getInstance(project).snapshotOrNull() ?: return null
  val evoPyProject = if (module == null) snapshot.main else snapshot.forModule(module)
  return evoPyProject?.interpreter
}

/**
 * Whether a run configuration writes the legacy fields `SDK_NAME` and `SDK_HOME`. Only the legacy flow writes them, see
 * [isInterpreterLibraryFlowEnabled]. The interpreter library flow reads them once, to migrate a run configuration of an
 * older build, and keeps the values it read.
 */
@ApiStatus.Internal
fun isLegacySdkWriteEnabled(): Boolean = !isInterpreterLibraryFlowEnabled()


/** The root of the tool workspace of [module] in the current snapshot, or the main Python project without [module]. */
private fun workspaceRootOf(project: Project, module: Module?): EvoPyProject? {
  val snapshot = EvoPyProjectModel.getInstance(project).snapshotOrNull() ?: return null
  if (module == null) return snapshot.main
  val evoPyProject = snapshot.forModule(module) ?: return null
  return snapshot.workspaces.firstOrNull { workspace -> workspace.members.any { it === evoPyProject } }?.root
}
