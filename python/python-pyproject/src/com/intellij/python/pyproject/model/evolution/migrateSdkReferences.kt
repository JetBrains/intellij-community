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
import com.intellij.platform.workspace.storage.entities
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.pyproject.model.internal.pyProject.PyProjectImpl
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.jetbrains.python.PyNames
import com.jetbrains.python.sdk.internal.PYTHON_FACET_ID
import com.jetbrains.python.sdk.legacy.importTableSdk
import com.jetbrains.python.sdk.legacy.transferredPathsOfTableSdk
import java.nio.file.Path
import com.intellij.python.sdk.backend.PyInterpreterLibraryName
import com.intellij.python.sdk.backend.PythonInterpreterKey
import com.intellij.python.sdk.backend.interpreterOwnerProjectDir
import com.intellij.python.sdk.backend.setPyInterpreterDependency

private val logger = fileLogger()

/**
 * Replaces the Python SDK reference of each module of [project], see [ownSdkReferenceOf], with an interpreter library
 * entry, see [PyInterpreterLibraryName]. An older build and the facet of a module of another type store such a
 * reference: the Python SDK of a module, and the SDK name of a Python facet. An inherited project SDK is not one: a
 * module does not inherit its interpreter. The entry then names the interpreter, so a module of another type, such as a
 * Java module with its own Python SDK, is a Python project.
 *
 * Call it only after the synchronization with the JPS model, so the SDK table is complete. A module that has an entry
 * keeps it. A module without one gets the entry of the SDK, which the registry imports from the SDK table into its
 * store, owned by the PyProject of the module, see [importTableSdk]. This is the only read of
 * the table. Then the reference goes in each case, also when the table has no such SDK, so the entry is the only thing
 * that names the interpreter, and a removed entry does not come back. An SDK whose environment is gone gives an entry,
 * and its interpreter appears when the environment comes back.
 *
 * The model calls this function on each pass, so a module that an older build gives an SDK later is migrated too.
 */
internal suspend fun migrateSdkReferences(project: Project) {
  val projectDir = project.interpreterOwnerProjectDir ?: return
  val storage = project.workspaceModel.currentSnapshot
  val modules = storage.entities<ModuleEntity>().filter { ownSdkReferenceOf(it) != null }.toList()
  if (modules.isEmpty()) return

  val registry = PythonInterpreterRegistry.getInstance(project)
  val lost = mutableListOf<MigratedModule>()
  val migrations = modules.mapNotNull { module ->
    val sdkName = ownSdkReferenceOf(module) ?: return@mapNotNull null
    val hasEntry = interpreterLibraryOf(module) != null
    val entry = if (hasEntry) null else entryOf(module, sdkName, projectDir, registry)
    MigratedModule(module.symbolicId, sdkName, entry, registry.transferredPathsOfTableSdk(sdkName))
      .also { if (!hasEntry && entry == null) lost += it }
  }
  logger.info("Replace the Python SDK references of ${migrations.size} modules: $migrations")
  if (lost.isNotEmpty()) {
    logger.warn("The SDK table has no Python SDK for ${lost.size} modules, so they lose their interpreter: $lost")
  }

  project.workspaceModel.update("Replace the Python SDK references of the modules") { mutable ->
    for ((moduleId, _, entry, transferredUrls) in migrations) {
      val module = mutable.resolve(moduleId) ?: continue
      mutable.modifyModuleEntity(module) {
        // Another writer can have given the module an entry since the read, and that entry wins.
        if (entry != null && interpreterLibraryOf(module) == null) dependencies.setPyInterpreterDependency(entry)
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
}

/**
 * The entry of the SDK named [sdkName] for [module], or `null` when the SDK table has no such Python SDK. The store of
 * the registry holds no SDK of the table, so the SDK is imported for the PyProject of the module. The module needs no
 * other sign of a Python project: the entry makes it one, see PyProjectImpl.baseDirOf.
 */
private fun entryOf(module: ModuleEntity, sdkName: String, projectDir: Path, registry: PythonInterpreterRegistry): PyInterpreterLibraryName? {
  val ownerDir = PyProjectImpl.baseDirOfRoots(module) ?: return null
  val key = registry.importTableSdk(sdkName, ownerDir) ?: return null
  return PyInterpreterLibraryName.of(projectDir, key)
}

/**
 * A module whose reference to the SDK named [sdkName] [migrateSdkReferences] removes. [entry] is the interpreter library
 * it gets, or `null` when it keeps its own entry or the SDK table has no such SDK. [transferredUrls] are the source
 * roots that the legacy flow transferred to it from its SDK.
 */
private data class MigratedModule(
  val moduleId: ModuleId,
  val sdkName: String,
  val entry: PyInterpreterLibraryName?,
  val transferredUrls: Set<String>,
)

/** The name of the library of [interpreter] in the project at [projectDir]. */
internal fun libraryNameOf(projectDir: Path, interpreter: PythonInterpreter): PyInterpreterLibraryName =
  PyInterpreterLibraryName.of(projectDir, PythonInterpreterKey(interpreter.ownerDir, interpreter.ref))

private fun FacetEntity.isPythonFacetWithSdk(): Boolean =
  typeId.name == PYTHON_FACET_ID && configurationXmlTag?.let(::facetSdkName) != null

