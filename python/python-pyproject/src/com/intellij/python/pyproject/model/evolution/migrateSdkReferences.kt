// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.evolution

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.JpsFileDependentEntitySource
import com.intellij.platform.workspace.jps.JpsFileEntitySource
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
 * this flow, so a module that inherits the project SDK gets the entry of that SDK, and the project SDK goes.
 * A project SDK of another type, such as a JDK, stays.
 *
 * Call it only after the synchronization with the JPS model, so the SDK table is complete. A module that has an entry
 * keeps it. A module without one gets the entry of the SDK, which the registry imports from the SDK table into its
 * store, owned by the PyProject of the module, see [importTableSdk]. This is the only read of
 * the table. Then the reference goes in each case, also when the table has no such SDK, so the entry is the only thing
 * that names the interpreter, and a removed entry does not come back. An SDK whose environment is gone gives an entry,
 * and its interpreter appears when the environment comes back.
 *
 * The Python facet is a legacy carrier of a Python module. Each module with a content root loses it, see
 * [hasLegacyPythonFacet]. A facet that names an SDK goes with the migration of its module, also when the SDK table has no
 * such SDK. The Python module type stays: it still makes a module a Python project, see [PyProjectImpl.baseDirOf].
 *
 * [PySdkReferenceMigration] calls this function on each change of the modules, so a module that an older build gives an
 * SDK later is migrated too.
 */
internal suspend fun migrateSdkReferences(project: Project) {
  val projectDir = project.interpreterOwnerProjectDir ?: return
  val storage = project.workspaceModel.currentSnapshot
  val projectSdkName = pythonProjectSdkNameOf(storage)
  val references = storage.entities<ModuleEntity>().mapNotNull { module ->
    val sdkName = ownSdkReferenceOf(module) ?: projectSdkName?.takeIf { inheritsProjectSdk(module) }
    sdkName?.let { module to it }
  }.toList()
  if (references.isEmpty() && projectSdkName == null && storage.entities<ModuleEntity>().none { it.hasLegacyPythonFacet() }) return

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
    for (module in mutable.entities<ModuleEntity>().filter { it.hasLegacyPythonFacet() }.toList()) {
      // Another writer can have given the module a facet with an SDK since the read. That facet waits for the next pass.
      val facets = module.facets.filter { it.isPythonFacet() }
        .filter { it.facetSdkName() == null || module.symbolicId in migrated || interpreterLibraryOf(module) != null }
      facets.forEach(mutable::removeEntity)
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

/**
 * Whether [module] has a content root and inherits the project SDK. The module needs no other sign of a Python project:
 * a Python project SDK that it inherits is its interpreter, and the entry of that SDK makes it a Python project.
 */
private fun inheritsProjectSdk(module: ModuleEntity): Boolean =
  module.dependencies.any { it is InheritedSdkDependency } && PyProjectImpl.baseDirOfRoots(module) != null

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

/**
 * Whether [this] module has a content root and a Python facet, which the migration removes. Only a module that a project
 * file stores, such as an `.iml`, has it from an older build. An importer makes its modules again on each sync, so the
 * migration leaves them as the importer writes them.
 */
private fun ModuleEntity.hasLegacyPythonFacet(): Boolean =
  isStoredInProjectFile() && contentRoots.isNotEmpty() && facets.any { it.isPythonFacet() }

private fun ModuleEntity.isStoredInProjectFile(): Boolean =
  entitySource is JpsFileEntitySource || entitySource is JpsFileDependentEntitySource

