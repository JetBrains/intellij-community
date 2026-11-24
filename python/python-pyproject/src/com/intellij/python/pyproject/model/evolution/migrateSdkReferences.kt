// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.evolution

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ProjectSettingsEntity
import com.intellij.platform.workspace.jps.entities.SdkDependency
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.jps.entities.modifyProjectSettingsEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.pyproject.model.internal.pyProject.PyProjectImpl
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.jetbrains.python.PyNames
import com.jetbrains.python.sdk.internal.PYTHON_FACET_ID
import com.jetbrains.python.sdk.internal.PYTHON_MODULE_ID
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
 * reference: the Python SDK of a module, and the SDK name of a Python facet. The entry then names the interpreter, so a
 * module of another type, such as a Java module with its own Python SDK, is a Python project.
 *
 * An older build also stores a Python project SDK, which a module inherits. A module does not inherit its interpreter in
 * this flow, so a Python project that inherits the project SDK gets the entry of that SDK, and the project SDK goes.
 * A project SDK of another type, such as a JDK, stays.
 *
 * Call it only after the synchronization with the JPS model, so the SDK table is complete. A module that has an entry
 * keeps it. A module without one gets the entry of the SDK, which the registry imports from the SDK table into its
 * store, owned by the PyProject of the module, see [importTableSdk]. This is the only read of
 * the table. Then the reference goes in each case, also when the table has no such SDK, so the entry is the only thing
 * that names the interpreter, and a removed entry does not come back. An SDK whose environment is gone gives an entry,
 * and its interpreter appears when the environment comes back.
 *
 * The Python module type and the Python facet are legacy carriers of a Python module, see [PyProjectImpl.baseDirOf]. Each
 * module with a content root loses both. A facet that names an SDK goes only after its SDK gave the module an entry.
 *
 * The model calls this function on each pass, so a module that an older build gives an SDK later is migrated too.
 */
internal suspend fun migrateSdkReferences(project: Project) {
  val projectDir = project.interpreterOwnerProjectDir ?: return
  val storage = project.workspaceModel.currentSnapshot
  val projectSdkName = pythonProjectSdkNameOf(storage)
  val references = storage.entities<ModuleEntity>().mapNotNull { module ->
    val sdkName = ownSdkReferenceOf(module) ?: projectSdkName?.takeIf { inheritsProjectSdk(module) }
    sdkName?.let { module to it }
  }.toList()
  if (references.isEmpty() && projectSdkName == null && storage.entities<ModuleEntity>().none { it.hasLegacyPythonCarrier() }) return

  val registry = PythonInterpreterRegistry.getInstance(project)
  val lost = mutableListOf<MigratedModule>()
  val migrations = references.map { (module, sdkName) ->
    val hasEntry = interpreterLibraryOf(module) != null
    val entry = if (hasEntry) null else entryOf(module, sdkName, projectDir, registry)
    MigratedModule(module.symbolicId, sdkName, entry, registry.transferredPathsOfTableSdk(sdkName))
      .also { if (!hasEntry && entry == null) lost += it }
  }
  logger.info("Replace the Python SDK references of ${migrations.size} modules and the project SDK $projectSdkName: $migrations")
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
      // The legacy flow wrote the in-project paths of the SDK to the `.iml` as source roots. This flow derives them, see
      // PyInterpreterLibrarySync, so the stored ones go.
      val transferred = module.contentRoots.flatMap { it.sourceRoots }
        .filter { it.url.url in transferredUrls && it.entitySource != PyInterpreterLibraryEntitySource }
      transferred.forEach(mutable::removeEntity)
    }
    val migrated = migrations.mapTo(HashSet()) { it.moduleId }
    for (module in mutable.entities<ModuleEntity>().filter { it.hasLegacyPythonCarrier() }.toList()) {
      // Another writer can have given the module a facet with an SDK since the read. That facet waits for the next pass.
      val facets = module.facets.filter { it.isPythonFacet() }
        .filter { it.facetSdkName() == null || module.symbolicId in migrated || interpreterLibraryOf(module) != null }
      facets.forEach(mutable::removeEntity)
      if (module.type?.name == PYTHON_MODULE_ID) mutable.resolve(module.symbolicId)?.let { mutable.modifyModuleEntity(it) { type = null } }
    }
    // Another writer can have changed the project SDK since the read.
    val settings = mutable.entities<ProjectSettingsEntity>().firstOrNull()
    if (settings != null && pythonProjectSdkNameOf(mutable) != null) {
      mutable.modifyProjectSettingsEntity(settings) { projectSdk = null }
    }
  }
}

/** The name of the project SDK in [storage] when it is a Python SDK, or `null`. */
private fun pythonProjectSdkNameOf(storage: EntityStorage): String? =
  storage.entities<ProjectSettingsEntity>().firstOrNull()?.projectSdk?.takeIf { it.type == PyNames.PYTHON_SDK_ID_NAME }?.name

/** Whether [module] is a Python project that inherits the project SDK. */
private fun inheritsProjectSdk(module: ModuleEntity): Boolean =
  module.dependencies.any { it is InheritedSdkDependency } && PyProjectImpl.baseDirOf(module) != null

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

private fun FacetEntity.isPythonFacet(): Boolean = typeId.name == PYTHON_FACET_ID

private fun FacetEntity.facetSdkName(): String? = configurationXmlTag?.let(::facetSdkName)

/** Whether [this] module has a content root and the Python module type or a Python facet, which the migration removes. */
private fun ModuleEntity.hasLegacyPythonCarrier(): Boolean =
  contentRoots.isNotEmpty() && (type?.name == PYTHON_MODULE_ID || facets.any { it.isPythonFacet() })

