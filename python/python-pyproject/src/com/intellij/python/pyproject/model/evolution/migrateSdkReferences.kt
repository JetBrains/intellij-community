// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.evolution

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.SdkDependency
import com.intellij.platform.workspace.jps.entities.modifyFacetEntity
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.pyproject.model.internal.pyProject.PyProjectImpl
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.jetbrains.python.PyNames
import com.jetbrains.python.sdk.internal.PYTHON_FACET_ID
import java.nio.file.Path
import com.intellij.python.sdk.backend.PyInterpreterLibraryName
import com.intellij.python.sdk.backend.PythonInterpreterKey
import com.intellij.python.sdk.backend.setPyInterpreterDependency

private val logger = fileLogger()

/**
 * Gives an interpreter library entry (see [PyInterpreterLibraryName]) to each module of [project] that refers to a
 * Python SDK and has no entry yet. An older build and the facet of a module of another type store such a reference:
 * the Python SDK of a module, and the SDK name of a Python facet. An inherited project SDK is not one: a Python module
 * does not inherit its interpreter.
 *
 * The migration removes these references, so a removed entry does not come back from them. The model calls this
 * function on each pass, so a module that an older build gives an SDK later, without an entry, also gets one.
 *
 * The registry imports the SDK of the reference from the SDK table into its store, owned by the PyProject of the
 * module, see [PythonInterpreterRegistry.importTableSdk]. This is the only read of the table. A reference to an SDK
 * that the table does not have gives no entry: the table can still be loading, so a later pass gives it. An SDK whose
 * environment is gone gives an entry, and its interpreter appears when the environment comes back. Returns whether a
 * module still has such a reference.
 */
internal suspend fun migrateSdkReferences(project: Project): Boolean {
  val projectDir = project.basePath?.let { Path.of(it) } ?: return false
  val storage = project.workspaceModel.currentSnapshot
  val registry = PythonInterpreterRegistry.getInstance(project)
  val modules = storage.entities<ModuleEntity>().filter { it.hasPendingSdkReference() }.toList()
  if (modules.isEmpty()) return false

  val entries = modules.mapNotNull { module ->
    val sdkName = pendingSdkNameOf(module) ?: return@mapNotNull null
    // The store of the registry holds no SDK of the table, so the SDK is imported for the PyProject of the module.
    val ownerDir = PyProjectImpl.baseDirOf(module) ?: return@mapNotNull null
    val key = registry.importTableSdk(sdkName, ownerDir) ?: return@mapNotNull null
    MigratedModule(module.symbolicId, PyInterpreterLibraryName.of(projectDir, key), registry.transferredPathsOfTableSdk(sdkName))
  }
  if (entries.isEmpty()) return true
  logger.info("Give interpreter libraries to ${entries.size} modules with a Python SDK reference: $entries")

  project.workspaceModel.update("Give interpreter libraries to the modules with a Python SDK") { mutable ->
    for ((moduleId, name, transferredUrls) in entries) {
      val module = mutable.resolve(moduleId)?.takeIf { interpreterLibraryOf(it) != name } ?: continue
      mutable.modifyModuleEntity(module) {
        dependencies.setPyInterpreterDependency(name)
        dependencies.removeAll { it is SdkDependency && it.sdk.type == PyNames.PYTHON_SDK_ID_NAME }
      }
      for (facet in module.facets.filter { it.isPythonFacetWithSdk() }) {
        mutable.modifyFacetEntity(facet) { configurationXmlTag = configurationXmlTag?.let(::withoutFacetSdkName) }
      }
      // The legacy flow wrote the in-project paths of the SDK to the `.iml` as source roots. This flow derives them, see
      // PyInterpreterLibrarySync, so the stored ones go.
      val transferred = module.contentRoots.flatMap { it.sourceRoots }
        .filter { it.url.url in transferredUrls && it.entitySource != PyInterpreterLibraryEntitySource }
      transferred.forEach(mutable::removeEntity)
    }
  }
  return entries.size < modules.size
}

/**
 * A module that [migrateSdkReferences] gives the interpreter library [name]. [transferredUrls] are the source roots
 * that the legacy flow transferred to it from its SDK.
 */
private data class MigratedModule(val moduleId: ModuleId, val name: PyInterpreterLibraryName, val transferredUrls: Set<String>)

/**
 * Whether a module of [storage] still refers to a Python SDK in a way that [migrateSdkReferences] replaces. It writes
 * nothing, so the model can call it inside a pass.
 */
internal fun hasPendingSdkReferences(project: Project, storage: EntityStorage): Boolean {
  if (project.basePath == null) return false
  return storage.entities<ModuleEntity>().any { it.hasPendingSdkReference() }
}

private fun ModuleEntity.hasPendingSdkReference(): Boolean =
  pendingSdkNameOf(this) != null

/**
 * The SDK name that [migrateSdkReferences] imports for [module], or `null` when the module needs no migration. A module
 * without an entry gives its Python SDK, else the SDK name of its Python facet. A module with an entry is migrated.
 */
private fun pendingSdkNameOf(module: ModuleEntity): String? {
  if (interpreterLibraryOf(module) != null) return null
  return module.dependencies.firstNotNullOfOrNull { (it as? SdkDependency)?.sdk?.takeIf { sdk -> sdk.type == PyNames.PYTHON_SDK_ID_NAME }?.name }
         ?: module.facets.firstOrNull { it.typeId.name == PYTHON_FACET_ID }?.configurationXmlTag?.let(::facetSdkName)
}

/** The name of the library of [interpreter] in the project at [projectDir]. */
internal fun libraryNameOf(projectDir: Path, interpreter: PythonInterpreter): PyInterpreterLibraryName =
  PyInterpreterLibraryName.of(projectDir, PythonInterpreterKey(interpreter.ownerDir, interpreter.ref))

private fun FacetEntity.isPythonFacetWithSdk(): Boolean =
  typeId.name == PYTHON_FACET_ID && configurationXmlTag?.let(::facetSdkName) != null

