package com.intellij.python.pyproject.model.internal

import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemRefreshStatus
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.removeUserData
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.model.spi.*
import com.jetbrains.python.venvReader.Directory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.pathString


// Workspace adapter functions

internal fun isProjectLinked(project: Project): Boolean =
  project.workspaceModel.currentSnapshot.entitiesBySource(sourceFilter = { it is PyProjectTomlEntitySource }).any()

internal suspend fun unlinkProject(project: Project, externalProjectPath: String) {
  val tracker = ExternalSystemProjectTracker.getInstance(project)
  project.getUserData(PY_PROJECT_TOML_KEY)?.let { oldProjectId ->
    tracker.remove(oldProjectId)
  }
  project.removeUserData(PY_PROJECT_TOML_KEY)
  withContext(Dispatchers.Default) {
    project.workspaceModel.update(PyProjectTomlBundle.message("intellij.python.pyproject.unlink.model")) { storage ->
      storage.replaceBySource({ it is PyProjectTomlEntitySource }, MutableEntityStorage.create())
    }
  }
  project.messageBus.syncPublisher(PROJECT_LINKER_AWARE_TOPIC).onProjectUnlinked(externalProjectPath)
}

internal suspend fun linkProject(project: Project, projectModelRoot: Path) {
  val externalProjectPath = projectModelRoot.pathString
  val (files, excludeDirs) = walkFileSystem(projectModelRoot)
  val entries = generatePyProjectTomlEntries(files, excludeDirs)
  unlinkProject(project, externalProjectPath)

  val tracker = ExternalSystemProjectTracker.getInstance(project)
  val projectAware = PyExternalSystemProjectAware(ExternalSystemProjectId(SYSTEM_ID, externalProjectPath), files.map { it.key.pathString }.toSet(), project, projectModelRoot)
  tracker.register(projectAware)
  tracker.activate(projectAware.projectId)
  project.putUserData(PY_PROJECT_TOML_KEY, projectAware.projectId)
  val storage = createEntityStorage(project, entries, project.workspaceModel.getVirtualFileUrlManager())
  val listener = project.messageBus.syncPublisher(PROJECT_AWARE_TOPIC)
  listener.onProjectReloadStart()
  try {
    project.workspaceModel.update(PyProjectTomlBundle.message("action.PyProjectTomlSyncAction.description")) { mutableStorage -> // Fake module entity is added by default if nothing was discovered
      removeFakeModuleEntity(mutableStorage)
      mutableStorage.replaceBySource({ it is PyProjectTomlEntitySource }, storage)
    }
  }
  catch (e: Exception) {
    listener.onProjectReloadFinish(ExternalSystemRefreshStatus.FAILURE)
    throw e
  }
  listener.onProjectReloadFinish(ExternalSystemRefreshStatus.SUCCESS)
  project.messageBus.syncPublisher(PROJECT_LINKER_AWARE_TOPIC).onProjectLinked(externalProjectPath)
}

private suspend fun generatePyProjectTomlEntries(files: Map<Path, PyProjectToml>, allExcludeDirs: Set<Directory>): Set<PyProjectTomlBasedEntryImpl> = withContext(Dispatchers.Default) {
  val entries = ArrayList<PyProjectTomlBasedEntryImpl>()
  // Any tool that helped us somehow must be tracked here
  for ((tomlFile, toml) in files.entries) {
    val participatedTools = mutableSetOf<ToolId>()
    val root = tomlFile.parent
    var projectNameAsString = toml.project?.name
    if (projectNameAsString == null) {
      val toolAndName = getNameFromEP(toml)
      if (toolAndName != null) {
        projectNameAsString = toolAndName.second
        participatedTools.add(toolAndName.first.id)
      }
    }
    val projectName = ProjectName(projectNameAsString ?: "${root.name}@${tomlFile.hashCode()}")
    val sourceRootsAndTools = Tool.EP.extensionList.flatMap { tool -> tool.getSrcRoots(toml.toml, root).map { Pair(tool, it) } }.toSet()
    val sourceRoots = sourceRootsAndTools.map { it.second }.toSet()
    participatedTools.addAll(sourceRootsAndTools.map { it.first.id })
    val excludedDirs = allExcludeDirs.filter { it.startsWith(root) }
    val relationsWithTools: List<PyProjectTomlToolRelation> = participatedTools.map { PyProjectTomlToolRelation.SimpleRelation(it) }
    val entry = PyProjectTomlBasedEntryImpl(tomlFile, HashSet(relationsWithTools), toml, projectName, root, mutableSetOf(), sourceRoots, excludedDirs.toSet())
    entries.add(entry)
  }
  val entriesByName = entries.associateBy { it.name }
  val namesByDir = entries.associate { Pair(it.root, it.name) }
  val allNames = entriesByName.keys
  for (tool in Tool.EP.extensionList) {
    val (dependencies, workspaceMembers) = tool.getProjectStructure(entriesByName, namesByDir)
    for ((name, deps) in dependencies) {
      val orphanNames = deps - allNames
      assert(orphanNames.isEmpty()) { "Tool $tool retuned wrong project names ${orphanNames.joinToString(", ")}" }
      val entity = entriesByName[name] ?: error("Tool $tool returned broken name $name")
      entity.dependencies.addAll(deps)
      if (deps.isNotEmpty()) {
        entity.relationsWithTools.add(PyProjectTomlToolRelation.SimpleRelation(tool.id))
      }
      workspaceMembers[entity.name]?.let { workspace ->
        entity.relationsWithTools.add(PyProjectTomlToolRelation.WorkspaceMember(tool.id, workspace))
      }
    }
  }
  return@withContext entries.toSet()
}

private suspend fun getNameFromEP(projectToml: PyProjectToml): Pair<Tool, @NlsSafe String>? = withContext(Dispatchers.Default) {
  Tool.EP.extensionList.firstNotNullOfOrNull { tool -> tool.getProjectName(projectToml.toml)?.let { Pair(tool, it) } }
}

private suspend fun createEntityStorage(
  project: Project,
  graph: Set<PyProjectTomlBasedEntryImpl>,
  virtualFileUrlManager: VirtualFileUrlManager,
): EntityStorage = withContext(Dispatchers.Default) {
  val fileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
  val storage = MutableEntityStorage.create()
  for (pyProject in graph) {
    val existingModuleEntity = project.workspaceModel.currentSnapshot
      .entitiesBySource { it is PyProjectTomlEntitySource }
      .filterIsInstance<ModuleEntity>()
      .find { it.name == pyProject.name.name }
    val existingSdkEntity = existingModuleEntity
      ?.dependencies
      ?.find { it is SdkDependency } as? SdkDependency
    val sdkDependency = existingSdkEntity ?: InheritedSdkDependency
    val entitySource = PyProjectTomlEntitySource(pyProject.tomlFile.toVirtualFileUrl(virtualFileUrlManager))
    val moduleEntity = storage addEntity ModuleEntity(pyProject.name.name, emptyList(), entitySource) {
      dependencies += sdkDependency
      dependencies += ModuleSourceDependency
      for (moduleName in pyProject.dependencies) {
        dependencies += ModuleDependency(ModuleId(moduleName.name), true, DependencyScope.COMPILE, false)
      }
      contentRoots = listOf(ContentRootEntity(pyProject.root.toVirtualFileUrl(fileUrlManager), emptyList(), entitySource) {
        sourceRoots = pyProject.sourceRoots.map { srcRoot ->
          SourceRootEntity(srcRoot.toVirtualFileUrl(fileUrlManager), PYTHON_SOURCE_ROOT_TYPE, entitySource)
        }
        excludedUrls = pyProject.excludedRoots.map { excludedRoot ->
          ExcludeUrlEntity(excludedRoot.toVirtualFileUrl(fileUrlManager), entitySource)
        }
      })
      val participatedTools: MutableMap<ToolId, ModuleId?> = pyProject.relationsWithTools.associate { Pair(it.toolId, null) }.toMutableMap()
      for (relation in pyProject.relationsWithTools) {
        when (relation) {
          is PyProjectTomlToolRelation.SimpleRelation -> Unit
          is PyProjectTomlToolRelation.WorkspaceMember -> {
            participatedTools[relation.toolId] = ModuleId(relation.workspace.name)
          }
        }
      }

      pyProjectTomlEntity = PyProjectTomlWorkspaceEntity(participatedTools = participatedTools, entitySource)
      exModuleOptions = ExternalSystemModuleOptionsEntity(entitySource) {
        externalSystem = PYTHON_SOURCE_ROOT_TYPE.name
      }
    }
    moduleEntity.symbolicId
  }
  return@withContext storage
}

private class PyProjectTomlEntitySource(tomlFile: VirtualFileUrl) : EntitySource {
  override val virtualFileUrl: VirtualFileUrl = tomlFile
}

// For the time being mark them as java-sources to indicate that in the Project tool window
private val PYTHON_SOURCE_ROOT_TYPE: SourceRootTypeId = SourceRootTypeId("java-source")
private val PY_PROJECT_TOML_KEY = Key.create<ExternalSystemProjectId>("pyProjectTomlAware")

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
 * @see com.intellij.openapi.project.impl.getOrInitializeModule
 */
private fun removeFakeModuleEntity(storage: MutableEntityStorage) {
  val contentRoots = storage
    .entitiesBySource { it !is PyProjectTomlEntitySource }
    .filterIsInstance<ContentRootEntity>()
    .toList()
  for (entity in contentRoots) {
    storage.removeEntity(entity.module)
    storage.removeEntity(entity)
  }
}

/**
 * What does [toolId] have to do with a certain projec?
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