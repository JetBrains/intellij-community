// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.evolution

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterKey
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.sdk.backend.isInterpreterLibraryFlowEnabled
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import com.intellij.python.sdk.backend.PyInterpreterLibraryName

/**
 * The owner directory and the ref of the interpreter that [name] names, or `null` when the project has no directory.
 * The module stores the interpreter of its [com.jetbrains.python.project.PyProject], so the name alone decides. A member
 * of a tool workspace names the interpreter of the workspace root.
 */
internal fun Project.keyOf(name: PyInterpreterLibraryName): PythonInterpreterKey? {
  val projectDir = basePath?.let { Path.of(it) } ?: return null
  return PythonInterpreterKey(name.ownerDir.resolveIn(projectDir), name.ref)
}

/**
 * What a module stores to name its interpreter: the interpreter library entry, see [PyInterpreterLibraryName], or the
 * name of its Python SDK when [isInterpreterLibraryFlowEnabled] is off, see [sdkReferenceOf].
 */
internal sealed interface StoredInterpreterReference {
  data class Library(val key: PythonInterpreterKey) : StoredInterpreterReference
  data class SdkName(val name: String) : StoredInterpreterReference
}

/** What [module] stores to name its interpreter, read from [storage], see [StoredInterpreterReference]. */
internal fun Project.storedInterpreterReferenceOf(module: ModuleEntity, storage: EntityStorage): StoredInterpreterReference? =
  if (isInterpreterLibraryFlowEnabled()) interpreterLibraryOf(module)?.let { keyOf(it) }?.let(StoredInterpreterReference::Library)
  else sdkReferenceOf(module, storage)?.let(StoredInterpreterReference::SdkName)

/** The interpreter that [reference] names, see [StoredInterpreterReference]. Waits for the first list of the registry. */
internal suspend fun PythonInterpreterRegistry.resolve(reference: StoredInterpreterReference): PythonInterpreter? = when (reference) {
  is StoredInterpreterReference.Library -> resolve(reference.key)
  is StoredInterpreterReference.SdkName -> findByModuleSdkName(reference.name)
}

/** What this module stores to name its interpreter now, see [StoredInterpreterReference], or `null`. */
private fun Module.storedInterpreterReference(): StoredInterpreterReference? {
  if (isDisposed) return null
  val storage = project.workspaceModel.currentSnapshot
  val module = findModuleEntity(storage) ?: return null
  return project.storedInterpreterReferenceOf(module, storage)
}

/**
 * The interpreter that this module stores now, see [StoredInterpreterReference], or `null`. It reads the module
 * itself, not the snapshot.
 *
 * For the code that writes interpreters and builds the project structure from them, such as the registry and the
 * configuration lock, which need the stored value at this instant. Every other caller asks the interpreter of its
 * Python project, `PyProject.getInterpreter`.
 */
@ApiStatus.Internal
suspend fun Module.readStoredInterpreter(): PythonInterpreter? =
  storedInterpreterReference()?.let { PythonInterpreterRegistry.getInstance(project).resolve(it) }
