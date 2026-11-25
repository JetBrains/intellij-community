// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.internal.workspaceBridge

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.jps.entities.modifyContentRootEntity
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.ApiStatus

/** The registry key that tells whether the `pyproject.toml` model owns its modules, see [modelOwnsModules]. */
@ApiStatus.Internal
const val PY_PROJECT_MODEL_OWNS_MODULES_KEY: String = "python.pyproject.model.owns.modules"

/**
 * Whether the `pyproject.toml` model owns the modules that it syncs. PyCharm sets [PY_PROJECT_MODEL_OWNS_MODULES_KEY] to
 * true: the model then takes over a module at the directory of a `pyproject.toml`, renames it, and syncs it fully. In
 * another IDE the modules belong to the user, see [applyKeepingModules].
 */
internal fun modelOwnsModules(): Boolean = Registry.`is`(PY_PROJECT_MODEL_OWNS_MODULES_KEY)

/** The registry key that tells whether the `pyproject.toml` model writes module dependencies, see [modelWritesModuleDependencies]. */
@ApiStatus.Internal
const val PY_PROJECT_MODEL_WRITES_MODULE_DEPENDENCIES_KEY: String = "python.pyproject.model.writes.module.dependencies"

/**
 * Whether the `pyproject.toml` model writes the dependencies of a `pyproject.toml` to its module as module dependencies.
 * When [PY_PROJECT_MODEL_WRITES_MODULE_DEPENDENCIES_KEY] is false, the model adds and removes no module dependency in
 * each mode. [PyProjectTomlWorkspaceEntity.dependencies] still holds the dependencies.
 */
internal fun modelWritesModuleDependencies(): Boolean = Registry.`is`(PY_PROJECT_MODEL_WRITES_MODULE_DEPENDENCIES_KEY)

/**
 * The source of a [PyProjectTomlWorkspaceEntity] on a module that the user owns. It is no file, so no `.iml` stores the
 * entity. The model writes it again on each rebuild.
 */
internal object PyProjectTomlKeptModuleEntitySource : EntitySource

/**
 * Applies [entries] outside PyCharm, where a module belongs to the user:
 * - a module at the directory of an entry keeps its name, its entity source and its own dependencies and roots. It gets
 *   only the source roots that it lacks, and the model records them as added;
 * - an entry without a module gets a regular module with an `.iml`. All its roots count as added;
 * - a module whose `pyproject.toml` is gone loses only its [PyProjectTomlWorkspaceEntity] and the roots that the model added.
 *
 * With [modelWritesModuleDependencies], a module also gets the module dependencies that it lacks. The model removes no
 * module dependency here, because it cannot tell its own from the ones of the user.
 *
 * A root that the model added and the user then removed is not added again. It stays in the record while the
 * `pyproject.toml` declares it, so each later rebuild still knows it.
 */
internal fun applyKeepingModules(entries: Set<PyProjectTomlBasedEntryImpl>, project: Project, storage: MutableEntityStorage) {
  val urlManager = project.workspaceModel.getVirtualFileUrlManager()
  val modules = storage.entities<ModuleEntity>().toList()
  val tomlModules = modules.filter { it.pyProjectTomlEntity != null }

  val matched = LinkedHashMap<PyProjectTomlBasedEntryImpl, ModuleEntity>()
  for (entry in entries) {
    val tomlDir = entry.tomlFile.parent.toVirtualFileUrl(urlManager)
    val root = entry.root.toVirtualFileUrl(urlManager)
    val claimed = matched.values.toSet()
    val module = tomlModules.firstOrNull { it !in claimed && it.pyProjectTomlEntity?.dirWithToml == tomlDir }
                 ?: modules.firstOrNull { it !in claimed && it.contentRoots.any { contentRoot -> contentRoot.url == root } }
    if (module != null) matched[entry] = module
  }

  // An entry names its dependencies by the names that the model gives the entries. A kept module has the name of the user.
  val moduleNameOfEntry = entries.associate { entry -> entry.name.name to (matched[entry]?.name ?: entry.name.name) }
  fun dependenciesOf(entry: PyProjectTomlBasedEntryImpl): List<String> =
    entry.dependencies.map { moduleNameOfEntry[it.name] ?: it.name }

  for (module in tomlModules - matched.values.toSet()) detach(module, storage)
  for ((entry, module) in matched) keep(entry, module, dependenciesOf(entry), storage, urlManager)
  for (entry in entries - matched.keys) addUserModule(entry, dependenciesOf(entry), project, storage, urlManager)
}

/** Removes the [PyProjectTomlWorkspaceEntity] of every module and the roots that the model added, for a model that is off. */
internal fun detachAllKeptModules(storage: MutableEntityStorage) {
  for (module in storage.entities<ModuleEntity>().filter { it.pyProjectTomlEntity != null }.toList()) detach(module, storage)
}

private fun keep(
  entry: PyProjectTomlBasedEntryImpl,
  module: ModuleEntity,
  entryDependencies: List<String>,
  storage: MutableEntityStorage,
  urlManager: VirtualFileUrlManager,
) {
  val known = module.pyProjectTomlEntity
  val dependencies = entryDependencies.filter { it != module.name }
  val sourceRoots = entry.sourceRoots.map { it.toVirtualFileUrl(urlManager) }

  val presentDependencies = module.dependencies.filterIsInstance<ModuleDependency>().mapTo(HashSet()) { it.module.name }
  val newDependencies = if (modelWritesModuleDependencies()) dependencies.filter { it !in presentDependencies } else emptyList()

  val contentRoot = module.contentRoots.firstOrNull { it.url == entry.root.toVirtualFileUrl(urlManager) }
  val presentRoots = module.contentRoots.flatMap { it.sourceRoots }.mapTo(HashSet()) { it.url }
  val (keptRoots, droppedRoots) = known?.addedSourceRoots.orEmpty().filter { it in presentRoots }.partition { it in sourceRoots }
  val rootsRemovedByUser = known?.addedSourceRoots.orEmpty().filter { it !in presentRoots && it in sourceRoots }
  val newRoots = if (contentRoot == null) emptyList() else sourceRoots.filter { it !in presentRoots && it !in rootsRemovedByUser }

  removeSourceRoots(module, droppedRoots.toSet(), storage)
  if (contentRoot != null && newRoots.isNotEmpty()) {
    storage.modifyContentRootEntity(contentRoot) {
      this.sourceRoots += newRoots.map { pyProjectSourceRoot(it, contentRoot.entitySource) }
    }
  }

  val participatedTools = buildParticipatedToolsMap(entry)
  val tomlDir = entry.tomlFile.parent.toVirtualFileUrl(urlManager)
  // The record keeps what the user removed, or the next rebuild would add it again.
  val addedSourceRoots = keptRoots + newRoots + rootsRemovedByUser
  storage.modifyModuleEntity(module) {
    this.dependencies += newDependencies.map(::moduleDependency)
    if (known == null) {
      this.pyProjectTomlEntity = PyProjectTomlWorkspaceEntity(
        participatedTools = participatedTools,
        dirWithToml = tomlDir,
        dependencies = dependencies,
        sourceRoots = sourceRoots,
        addedSourceRoots = addedSourceRoots,
        entitySource = PyProjectTomlKeptModuleEntitySource,
      )
    }
  }
  if (known != null) {
    storage.modifyPyProjectTomlWorkspaceEntity(known) {
      this.participatedTools = participatedTools
      this.dirWithToml = tomlDir
      this.dependencies = dependencies.toMutableList()
      this.sourceRoots = sourceRoots.toMutableList()
      this.addedSourceRoots = addedSourceRoots.toMutableList()
    }
  }
}

private fun addUserModule(
  entry: PyProjectTomlBasedEntryImpl,
  dependencies: List<String>,
  project: Project,
  storage: MutableEntityStorage,
  urlManager: VirtualFileUrlManager,
) {
  val entitySource = createEntitySourceImpl(project, null)
  val sourceRoots = entry.sourceRoots.map { it.toVirtualFileUrl(urlManager) }
  val writtenDependencies = if (modelWritesModuleDependencies()) dependencies else emptyList()
  storage addEntity ModuleEntity(entry.name.name, emptyList(), entitySource) {
    this.dependencies += ModuleSourceDependency
    this.dependencies += writtenDependencies.map(::moduleDependency)
    contentRoots = listOf(ContentRootEntity(entry.root.toVirtualFileUrl(urlManager), emptyList(), entitySource) {
      this.sourceRoots = sourceRoots.map { pyProjectSourceRoot(it, entitySource) }
    })
    pyProjectTomlEntity = PyProjectTomlWorkspaceEntity(
      participatedTools = buildParticipatedToolsMap(entry),
      dirWithToml = entry.tomlFile.parent.toVirtualFileUrl(urlManager),
      dependencies = dependencies,
      sourceRoots = sourceRoots,
      // The model made the module, so all its roots are the model's to change.
      addedSourceRoots = sourceRoots,
      entitySource = PyProjectTomlKeptModuleEntitySource,
    )
  }
}

private fun detach(module: ModuleEntity, storage: MutableEntityStorage) {
  val known = module.pyProjectTomlEntity ?: return
  removeSourceRoots(module, known.addedSourceRoots.toSet(), storage)
  storage.removeEntity(known)
}

private fun removeSourceRoots(module: ModuleEntity, urls: Set<VirtualFileUrl>, storage: MutableEntityStorage) {
  if (urls.isEmpty()) return
  module.contentRoots.flatMap { it.sourceRoots }.filter { it.url in urls }.forEach(storage::removeEntity)
}

private fun moduleDependency(module: String): ModuleDependency = ModuleDependency(ModuleId(module), true, DependencyScope.COMPILE, false)
