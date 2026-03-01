package com.intellij.python.pyproject.model.internal.workspaceBridge

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.JpsImportedEntitySource
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ContentRootEntityBuilder
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.ExternalSystemModuleOptionsEntity
import com.intellij.platform.workspace.jps.entities.FacetEntityBuilder
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.jps.entities.ModuleTypeId
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootTypeId
import com.intellij.platform.workspace.jps.entities.exModuleOptions
import com.intellij.platform.workspace.jps.entities.modifyContentRootEntity
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.jps.entities.sdkId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.createEntityTreeCopy
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.project.stateStore
import com.intellij.python.common.tools.ToolId
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.model.internal.PY_PROJECT_SYSTEM_ID
import com.intellij.python.pyproject.model.internal.PyProjectTomlBundle
import com.intellij.python.pyproject.model.internal.pyProjectToml.FSWalkInfoWithToml
import com.intellij.python.pyproject.model.internal.pyProjectToml.getDependenciesFromToml
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.pyproject.model.spi.PyProjectTomlProject
import com.intellij.python.pyproject.model.spi.Tool
import com.intellij.python.pyproject.model.spi.WorkspaceName
import com.intellij.python.pyproject.model.spi.plus
import com.intellij.workspaceModel.ide.legacyBridge.LegacyBridgeJpsEntitySourceFactory
import com.jetbrains.python.PyNames
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
    val entries = generatePyProjectTomlEntries(files)
    // No pyproject.toml files, no need to touch model at all
    if (entries.isEmpty()) {
      return
    }
    val syncStorage = createProjectModel(entries, project).toBuilder()
    project.workspaceModel.update(PyProjectTomlBundle.message("action.PyProjectTomlSyncAction.description")) { projectStorage -> // Fake module entity is added by default if nothing was discovered

      renameSameModule(syncStorage, projectStorage)
      relocateFacetAndSdk(syncStorage, projectStorage)
      projectStorage.replaceBySource({ it.isPythonEntity }, syncStorage)

      // For some reason, WSM duplicates sources instead of merging them, so we remove duplicates
      for (moduleEntity in projectStorage.entities(ModuleEntity::class.java).filter { it.entitySource.isPythonEntity }) {
        for (contentRoot in moduleEntity.contentRoots) {
          if (contentRoot.sourceRoots.size > 1) {
            projectStorage.modifyContentRootEntity(contentRoot) {
              removeSrcDuplicates()
            }
          }
        }
      }

    }
  }
}

private fun ContentRootEntityBuilder.removeSrcDuplicates() {
  val newSourceRoots = sourceRoots.distinctBy { it.url }
  sourceRoots = newSourceRoots
}

private fun renameSameModule(syncStorage: EntityStorage, projectStorage: MutableEntityStorage) {
  // TODO: fix O(N^2)
  for (syncModuleEntity in syncStorage.entities<ModuleEntity>()) {
    for (projectModuleEntity in projectStorage.entities<ModuleEntity>()) {
      if (ModuleAnchor(syncModuleEntity).sameAs(ModuleAnchor(projectModuleEntity))) {
        projectStorage.modifyModuleEntity(projectModuleEntity) {
          name = syncModuleEntity.name
          entitySource = syncModuleEntity.entitySource
        }
      }
    }
  }
}

private fun relocateFacetAndSdk(syncStorage: MutableEntityStorage, projectStorage: MutableEntityStorage) {
  for (syncModuleEntity in syncStorage.entities<ModuleEntity>()) {
    val projectModuleEntity = projectStorage.resolve(syncModuleEntity.symbolicId) ?: continue
    val projectFacetBuilders = projectModuleEntity.facets
      .map { it.createEntityTreeCopy() as FacetEntityBuilder }
    for (facetBuilder in projectFacetBuilders) {
      facetBuilder.entitySource = syncModuleEntity.entitySource
    }
    syncStorage.modifyModuleEntity(syncModuleEntity) {
      sdkId = projectModuleEntity.sdkId
      facets += projectFacetBuilders
    }
    for (entity in projectModuleEntity.contentRoots) {
      projectStorage.modifyContentRootEntity(entity) {
        this.entitySource = syncModuleEntity.entitySource
      }
    }
  }
}

/**
 * Return [entires_to_create_modules_from, dirs_to_exclude]
 */
private suspend fun generatePyProjectTomlEntries(
  fsInfo: FSWalkInfoWithToml,
): Set<PyProjectTomlBasedEntryImpl> = withContext(Dispatchers.Default) {
  val files = fsInfo.tomlFiles
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
    val sourceRootsAndTools = tools.flatMap { tool -> tool.getSrcRoots(toml.toml, root).map { Pair(tool, it) } }.toSet()
    val sourceRoots = sourceRootsAndTools.map { it.second }.toSet() + findSrc(root)
    participatedTools.addAll(sourceRootsAndTools.map { it.first.id })
    if (participatedTools.isEmpty()) {
      // If a tool is mentioned as a tool.<toolId> in pyproject.toml, we consider it participated in project configuration
      for (tool in tools) {
        if (toml.toml.contains("tool.${tool.id.id}")) {
          participatedTools.add(tool.id)
        }
      }
    }
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
                                            sourceRoots)
    entries.add(entry)
  }
  val entriesByName = entries.associateBy { it.name }
  val namesByDir = entries.associate { Pair(it.root, it.name) }
  val allNames = entriesByName.keys
  var dependencies = getDependenciesFromToml(entriesByName, namesByDir, tools.flatMap { it.getTomlDependencySpecifications() })
  for (tool in tools) {
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
  return@withContext entries.toSet()
}

private suspend fun Iterable<Tool>.getNameFromEP(projectToml: PyProjectToml): Pair<Tool, @NlsSafe String>? =
  withContext(Dispatchers.Default) {
    firstNotNullOfOrNull { tool -> tool.getProjectName(projectToml.toml)?.let { Pair(tool, it) } }
  }

private suspend fun createProjectModel(
  graph: Set<PyProjectTomlBasedEntryImpl>,
  project: Project,
): ImmutableEntityStorage = withContext(Dispatchers.Default) {
  val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
  val storage = MutableEntityStorage.create()
  for (pyProject in graph) {
    val moduleEntity = storage addEntity ModuleEntity(pyProject.name.name, emptyList(), createEntitySource(project)) {
      dependencies += ModuleSourceDependency
      for (moduleName in pyProject.dependencies) {
        dependencies += ModuleDependency(ModuleId(moduleName.name), true, DependencyScope.COMPILE, false)
      }
      contentRoots = listOf(ContentRootEntity(pyProject.root.toVirtualFileUrl(virtualFileUrlManager), emptyList(), entitySource) {
        sourceRoots = pyProject.sourceRoots.map { srcRoot ->
          SourceRootEntity(srcRoot.toVirtualFileUrl(virtualFileUrlManager), PYTHON_SOURCE_ROOT_TYPE, entitySource)
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
) : PyProjectTomlProject


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

private val EntitySource.isPythonEntity: Boolean get() = (this as? JpsImportedEntitySource)?.externalSystemId == PY_PROJECT_SYSTEM_ID.id

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

// Warning: this entity must be unique for each model, it can't be reused
internal fun createEntitySource(project: Project): EntitySource {
  val moduleRoot =
    project.stateStore.projectBasePath.resolve(DIRECTORY_STORE_FOLDER).toVirtualFileUrl(project.workspaceModel.getVirtualFileUrlManager())
  val externalSource = ExternalProjectSystemRegistry.getInstance()
    .getSourceById(PY_PROJECT_SYSTEM_ID.id)
  return LegacyBridgeJpsEntitySourceFactory.getInstance(project)
    .createEntitySourceForModule(moduleRoot, externalSource)
}
