package com.intellij.python.pyproject.model.internal.workspaceBridge

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.ExternalSystemModuleOptionsEntity
import com.intellij.platform.workspace.jps.entities.FacetEntityBuilder
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.jps.entities.ModuleTypeId
import com.intellij.platform.workspace.jps.entities.SdkDependency
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootTypeId
import com.intellij.platform.workspace.jps.entities.exModuleOptions
import com.intellij.platform.workspace.jps.entities.modifyContentRootEntity
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.jps.entities.sdkId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.createEntityTreeCopy
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.python.common.tools.ToolId
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.model.internal.PyProjectTomlBundle
import com.intellij.python.pyproject.model.internal.pyProjectToml.FSWalkInfoWithToml
import com.intellij.python.pyproject.model.internal.pyProjectToml.getPEP621Deps
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.pyproject.model.spi.PyModuleDataTransfer
import com.intellij.python.pyproject.model.spi.PyProjectTomlProject
import com.intellij.python.pyproject.model.spi.Tool
import com.intellij.python.pyproject.model.spi.WorkspaceName
import com.intellij.python.pyproject.model.spi.plus
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetModelBridge.Companion.findFacet
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkBridgeImpl.Companion.findSdkEntity
import com.intellij.workspaceModel.ide.isEqualOrParentOf
import com.jetbrains.python.PyNames
import com.jetbrains.python.facet.PythonFacetSettings
import com.jetbrains.python.venvReader.Directory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name


// Workspace adapter functions


internal suspend fun rebuildProjectModel(project: Project, files: FSWalkInfoWithToml) {
  changeWorkspaceMutex.withLock {
    val (entries, excludeDirs) = generatePyProjectTomlEntries(files)
    // No pyproject.toml files, no need to touch model at all
    if (entries.isEmpty()) {
      return
    }
    val newStorage = createEntityStorage(entries, project.workspaceModel.getVirtualFileUrlManager())

    val transfers = PyModuleDataTransfer.EP.extensionList.map { it.beforeRename(project) }
    val workspaceModel = project.workspaceModel
    val oldToNewModuleNames = object {
      lateinit var value: Map<String, String>
    }
    workspaceModel.update(PyProjectTomlBundle.message("action.PyProjectTomlSyncAction.description")) { currentStorage -> // Fake module entity is added by default if nothing was discovered
      oldToNewModuleNames.value = relocateUserDefinedModuleSdk(currentStorage) {
        removeFakeModuleAndConflictingEntities(currentStorage, newStorage.entities(ModuleEntity::class.java))
        currentStorage.replaceBySource({ it is PyProjectTomlEntitySource }, newStorage)

        // Exclude dirs
        if (excludeDirs.isEmpty()) return@relocateUserDefinedModuleSdk
        val modules = currentStorage.entities(ModuleEntity::class.java).toList()
        for (excludedRoot in excludeDirs.map { it.toVirtualFileUrl(workspaceModel.getVirtualFileUrlManager()) }) {
          currentStorage.excludeRoot(excludedRoot, modules)
        }
      }

    }
    for (transfer in transfers) {
      transfer.modulesRenamed(oldToNewModuleNames.value)
    }
  }
}

private fun MutableEntityStorage.excludeRoot(rootToExclude: VirtualFileUrl, modules: List<ModuleEntity>) {
  for (moduleEntry in modules) {
    for (rootEntity in moduleEntry.contentRoots) {
      if (rootEntity.url.isEqualOrParentOf(rootToExclude) && rootToExclude !in rootEntity.excludedUrls.map { it.url }) {
        modifyContentRootEntity(rootEntity) {
          excludedUrls = excludedUrls + listOf(
            ExcludeUrlEntity(rootToExclude, entitySource))
        }
        return
      }
    }
  }
}

/**
 * Helps [storage] to survive full module recreation by preserving SDK ids.
 *
 * For each module in [storage] stores `sdkId` and `moduleId`, then calls [transfer] and sets `sdkId` for modules with the same id
 */
internal fun relocateUserDefinedModuleSdk(storage: MutableEntityStorage, transfer: () -> Unit): Map<String, String> {
  val oldToNewName = mutableMapOf<String, String>()

  // Store SDK
  val pyModules = storage.entities(ModuleEntity::class.java).filter { it.isPythonModule }.toList()
  val moduleToSdkAndFacets = pyModules.map { moduleEntity ->
    val facets = moduleEntity.facets
    val sdkId = moduleEntity.sdkId
                // Module has no SDK, but might have a facet
                ?: facets.asSequence()
                  .filter { it.entitySource is PyProjectTomlEntitySource }
                  .mapNotNull { storage.findFacet(it) }
                  .map { it.configuration }
                  .filterIsInstance<PythonFacetSettings>()
                  .mapNotNull { storage.findSdkEntity(it.sdk) }
                  .map { it.symbolicId }
                  .firstOrNull()
    Pair(ModuleAnchor(moduleEntity),
         Triple(moduleEntity.facets.map { it.createEntityTreeCopy() as FacetEntityBuilder }, sdkId, moduleEntity.name))
  }

  transfer()

  for (newEntity in storage.entities(ModuleEntity::class.java)) {
    val newEntityAnchor = ModuleAnchor(newEntity)
    val (facetsToSet, sdkIdToSet, oldName) = moduleToSdkAndFacets.firstOrNull { it.first.sameAs(newEntityAnchor) }?.second ?: continue
    storage.modifyModuleEntity(newEntity) {
      if (this.sdkId == null) {
        this.sdkId = sdkIdToSet
      }
      for (facetEntityBuilder in facetsToSet) {
        facetEntityBuilder.module = this@modifyModuleEntity
        facetEntityBuilder.moduleId = newEntity.symbolicId
        this@modifyModuleEntity.facets += facetEntityBuilder
      }
    }
    oldToNewName[oldName] = newEntity.name
  }
  return oldToNewName
}

/**
 * Return [entires_to_create_modules_from, dirs_to_exclude]
 */
private suspend fun generatePyProjectTomlEntries(
  fsInfo: FSWalkInfoWithToml,
): Pair<Set<PyProjectTomlBasedEntryImpl>, Set<Directory>> = withContext(Dispatchers.Default) {
  val (files, allExcludeDirs) = fsInfo
  val entries = ArrayList<PyProjectTomlBasedEntryImpl>()
  val usedNamed = mutableSetOf<String>()
  val tools = Tool.EP.extensionList
  // Any tool that helped us somehow must be tracked here
  for ((tomlFile, toml) in files.entries) {
    val participatedTools = mutableSetOf<ToolId>()
    val root = tomlFile.parent
    var projectNameAsString = toml.project?.name
    if (projectNameAsString == null) {
      val toolAndName = tools.getNameFromEP(toml)
      if (toolAndName != null) {
        projectNameAsString = toolAndName.second
        participatedTools.add(toolAndName.first.id)
      }
    }
    if (projectNameAsString == null) {
      projectNameAsString = root.name
    }
    if (projectNameAsString in usedNamed) {
      projectNameAsString = "$projectNameAsString@${usedNamed.size}"
    }
    usedNamed.add(projectNameAsString)
    val projectName = ProjectName(projectNameAsString)
    val sourceRootsAndTools = Tool.EP.extensionList.flatMap { tool -> tool.getSrcRoots(toml.toml, root).map { Pair(tool, it) } }.toSet()
    val sourceRoots = sourceRootsAndTools.map { it.second }.toSet() + findSrc(root)
    participatedTools.addAll(sourceRootsAndTools.map { it.first.id })
    val excludedDirs = allExcludeDirs.filter { it.startsWith(root) }
    if (participatedTools.isEmpty()) {
      // Try to use build-tool as last resort
      toml.toml.getString("build-system.build-backend")?.let { buildBackend ->
        tools.firstOrNull { it.id.id in buildBackend }?.let { buildTool ->
          participatedTools.add(buildTool.id)
        }
      }
    }

    val relationsWithTools: List<PyProjectTomlToolRelation> = participatedTools.map { PyProjectTomlToolRelation.SimpleRelation(it) }
    val entry = PyProjectTomlBasedEntryImpl(tomlFile,
                                            HashSet(relationsWithTools),
                                            toml,
                                            projectName,
                                            root,
                                            mutableSetOf(),
                                            sourceRoots,
                                            excludedDirs.toSet())
    entries.add(entry)
  }
  val entriesByName = entries.associateBy { it.name }
  val namesByDir = entries.associate { Pair(it.root, it.name) }
  val allNames = entriesByName.keys
  var dependencies = getPEP621Deps(entriesByName, namesByDir)
  for (tool in Tool.EP.extensionList) {
    // Tool provides deps and workspace members
    val toolSpecificInfo = tool.getProjectStructure(entriesByName, namesByDir)
    // Tool-agnostic pep621 deps
    if (toolSpecificInfo != null) {
      dependencies += toolSpecificInfo.dependencies
      for (entityName in toolSpecificInfo.dependencies.map.keys) {
        val entity = entriesByName[entityName] ?: error("returned broken name $entityName")
        entity.relationsWithTools.add(PyProjectTomlToolRelation.SimpleRelation(tool.id))
      }
    }
    val workspaceMembers = toolSpecificInfo?.membersToWorkspace ?: emptyMap()

    for ((member, workspace) in workspaceMembers) {
      entriesByName[member]!!.relationsWithTools.add(PyProjectTomlToolRelation.WorkspaceMember(tool.id, workspace))
      entriesByName[workspace]!!.relationsWithTools.add(PyProjectTomlToolRelation.SimpleRelation(tool.id))
    }
  }
  for ((name, deps) in dependencies.map) {
    val orphanNames = deps - allNames
    assert(orphanNames.isEmpty()) { "wrong project names ${orphanNames.joinToString(", ")}" }
    val entity = entriesByName[name] ?: error("returned broken name $name")
    entity.dependencies.addAll(deps)
  }
  return@withContext Pair(entries.toSet(), allExcludeDirs)
}

private suspend fun Iterable<Tool>.getNameFromEP(projectToml: PyProjectToml): Pair<Tool, @NlsSafe String>? =
  withContext(Dispatchers.Default) {
    firstNotNullOfOrNull { tool -> tool.getProjectName(projectToml.toml)?.let { Pair(tool, it) } }
  }

private suspend fun createEntityStorage(
  graph: Set<PyProjectTomlBasedEntryImpl>,
  virtualFileUrlManager: VirtualFileUrlManager,
): ImmutableEntityStorage = withContext(Dispatchers.Default) {
  val storage = MutableEntityStorage.create()
  for (pyProject in graph) {
    val entitySource = PyProjectTomlEntitySource(pyProject.tomlFile.toVirtualFileUrl(virtualFileUrlManager))
    val moduleEntity = storage addEntity ModuleEntity(pyProject.name.name, emptyList(), entitySource) {
      dependencies += ModuleSourceDependency
      for (moduleName in pyProject.dependencies) {
        dependencies += ModuleDependency(ModuleId(moduleName.name), true, DependencyScope.COMPILE, false)
      }
      contentRoots = listOf(ContentRootEntity(pyProject.root.toVirtualFileUrl(virtualFileUrlManager), emptyList(), entitySource) {
        sourceRoots = pyProject.sourceRoots.map { srcRoot ->
          SourceRootEntity(srcRoot.toVirtualFileUrl(virtualFileUrlManager), PYTHON_SOURCE_ROOT_TYPE, entitySource)
        }
        excludedUrls = pyProject.excludedRoots.map { excludedRoot ->
          ExcludeUrlEntity(excludedRoot.toVirtualFileUrl(virtualFileUrlManager), entitySource)
        }
      })
      val participatedTools: MutableMap<ToolId, ModuleId?> =
        pyProject.relationsWithTools.associate { Pair(it.toolId, null) }.toMutableMap()
      for (relation in pyProject.relationsWithTools) {
        when (relation) {
          is PyProjectTomlToolRelation.SimpleRelation -> Unit
          is PyProjectTomlToolRelation.WorkspaceMember -> {
            participatedTools[relation.toolId] = ModuleId(relation.workspace.name)
          }
        }
      }

      type = PYTHON_MODULE_ID
      pyProjectTomlEntity =
        PyProjectTomlWorkspaceEntity(
          participatedTools = participatedTools, pyProject.tomlFile.parent.toVirtualFileUrl(
            virtualFileUrlManager
          ), entitySource
        )
      exModuleOptions = ExternalSystemModuleOptionsEntity(entitySource) {
        externalSystem = PYTHON_SOURCE_ROOT_TYPE.name
      }
    }
    moduleEntity.symbolicId
  }
  return@withContext storage.toSnapshot()
}


internal class PyProjectTomlEntitySource(tomlFile: VirtualFileUrl) : EntitySource {
  override val virtualFileUrl: VirtualFileUrl = tomlFile
}

// For the time being mark them as java-sources to indicate that in the Project tool window
private val PYTHON_SOURCE_ROOT_TYPE: SourceRootTypeId = SourceRootTypeId("java-source")

private data class PyProjectTomlBasedEntryImpl(
  val tomlFile: Path,
  val relationsWithTools: MutableSet<PyProjectTomlToolRelation>,
  override val pyProjectToml: PyProjectToml,
  val name: ProjectName,
  override val root: Directory,
  val dependencies: MutableSet<ProjectName>,
  val sourceRoots: Set<Directory>,
  val excludedRoots: Set<Directory>,
) : PyProjectTomlProject


/**
 * Removes the default IJ module created for the root of the project
 * (that's going to be replaced with a module belonging to a specific project management system).
 *
 * Removes JPS modules that happen to have the same root as `pyproject.toml` modules, as JPS modules are legacy.
 *
 * @see com.intellij.openapi.project.impl.getOrInitializeModule
 */
private fun removeFakeModuleAndConflictingEntities(storage: MutableEntityStorage, newModules: Sequence<ModuleEntity>) {
  val vfsManager = VirtualFileManager.getInstance()
  val contentsToRemove = newModules.flatMap { content -> content.contentRoots.map { vfsManager.findFileByUrl(it.url.url)!! } }.toSet()
  val namesToRemove = newModules.map { it.name.lowercase() }.toSet()
  val modulesToRemove = storage.entities(ModuleEntity::class.java)
    .filter { moduleEntity ->
      moduleEntity.type == PYTHON_MODULE_ID // Python module
      && (
        // Intersects with new module content root
        moduleEntity.contentRoots.map { vfsManager.findFileByUrl(it.url.url) }.any { it in contentsToRemove } ||
        // Intersects by name
        moduleEntity.name.lowercase() in namesToRemove ||
        // Auto-generated, temporary module
        moduleEntity.entitySource is NonPersistentEntitySource
         )
    }
    .toList()
  for (moduleToRemove in modulesToRemove) {
    storage.removeEntity(moduleToRemove)
  }
}

/**
 * What does [toolId] have to do with a certain project?
 */
private sealed interface PyProjectTomlToolRelation {
  val toolId: ToolId

  /**
   * There is something tool-specific in `pyproject.toml`
   */
  data class SimpleRelation(override val toolId: ToolId) : PyProjectTomlToolRelation

  /**
   * This module is a part of workspace, and the root is [workspace]
   */
  data class WorkspaceMember(override val toolId: ToolId, val workspace: WorkspaceName) : PyProjectTomlToolRelation
}


private val changeWorkspaceMutex = Mutex()

private suspend fun findSrc(root: Directory): Set<Directory> =
  withContext(Dispatchers.IO) {
    val src = root.resolve("src")
    if (src.exists()) setOf(src) else emptySet()
  }

private val PYTHON_MODULE_ID: ModuleTypeId = ModuleTypeId(PyNames.PYTHON_MODULE_ID)

private val ModuleEntity.sdkId: SdkId?
  get() = dependencies.firstNotNullOfOrNull {
    when (it) {
      InheritedSdkDependency, is LibraryDependency, is ModuleDependency, ModuleSourceDependency -> null
      is SdkDependency -> it.sdk
    }
  }

private val ModuleEntity.isPythonModule: Boolean get() = entitySource is PyProjectTomlEntitySource || type == PYTHON_MODULE_ID

private class ModuleAnchor(moduleEntity: ModuleEntity) {
  private val symbolicId = moduleEntity.symbolicId
  private val dirWithToml = moduleEntity.pyProjectTomlEntity?.dirWithToml
  private val theOnlyContentRoot = moduleEntity.contentRoots.let { if (it.size == 1) it[0] else null }

  fun sameAs(o: ModuleAnchor): Boolean =
    symbolicId == o.symbolicId ||
    symbolicId.name.equals(o.symbolicId.name, ignoreCase = true) ||
    (dirWithToml != null && dirWithToml == o.dirWithToml) ||
    (theOnlyContentRoot != null && theOnlyContentRoot.url == o.theOnlyContentRoot?.url)
}