package com.intellij.python.pyproject.model.internal.workspaceBridge

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.JpsImportedEntitySource
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ContentRootEntityBuilder
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntityBuilder
import com.intellij.platform.workspace.jps.entities.ExternalSystemModuleOptionsEntity
import com.intellij.platform.workspace.jps.entities.FacetEntityBuilder
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.jps.entities.ModuleTypeId
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntityBuilder
import com.intellij.platform.workspace.jps.entities.SourceRootTypeId
import com.intellij.platform.workspace.jps.entities.exModuleOptions
import com.intellij.platform.workspace.jps.entities.modifyContentRootEntity
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.jps.entities.sdkId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.createEntityTreeCopy
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
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
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.python.PyNames
import com.jetbrains.python.venvReader.Directory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name

private val logger = fileLogger()

// Workspace adapter functions


internal suspend fun rebuildProjectModel(project: Project, files: FSWalkInfoWithToml) {
  logger.debug {
    buildString {
      appendLine("Build request for files count ${files.tomlFiles.size}")
      for (file in files.tomlFiles.keys) {
        appendLine("Building model for file $file")
      }
    }
  }
  changeWorkspaceMutex.withLock {
    val entries = generatePyProjectTomlEntries(files)
    // No pyproject.toml files, no need to touch model at all
    if (entries.isEmpty()) {
      return
    }
    val syncStorage = createProjectModel(entries, project).toBuilder()
    project.workspaceModel.update(PyProjectTomlBundle.message("action.PyProjectTomlSyncAction.description")) { projectStorage -> // Fake module entity is added by default if nothing was discovered

      renameSameModuleAndMoveSources(syncStorage, projectStorage)
      relocateFacetAndSdk(syncStorage, projectStorage)
      logger.debug {
        buildString {
          val entities = syncStorage.entities<ModuleEntity>().toList()
          appendLine("Entities count: ${entities.size}")
          for (entity in entities) {
            appendLine("New entity ${entity.name}")
          }
        }
      }
      projectStorage.replaceBySource({ it.isPythonEntity }, syncStorage)
      ensureNoSrcIntersectsWithOtherRoots(projectStorage)
    }
  }
}

/**
 * No module should have source root that clashes with a content root of another module.
 * Starting from a module source and going up to its content root, we shouldn't meet any content root of another module.
 */
private fun ensureNoSrcIntersectsWithOtherRoots(projectStorage: MutableEntityStorage) {
  val moduleEntities = projectStorage.entities<ModuleEntity>().toList()
  val rootToModules = HashMap<Path, MutableSet<ModuleEntity>>()
  for (moduleEntity in moduleEntities) {
    for (contentRoot in moduleEntity.contentRoots) {
      rootToModules.getOrPut(contentRoot.url.toPath()) { mutableSetOf() }.add(moduleEntity)
    }
  }

  for (moduleEntity in moduleEntities) {
    for (contentRootEntity in moduleEntity.contentRoots.toList()) {
      if (contentRootEntity.sourceRoots.isEmpty()) continue

      val contentRoot = contentRootEntity.url.toPath()
      val clashingPaths = contentRootEntity.sourceRoots
        .mapNotNull { sr -> sr.url.toPath().takeIf { clashesWithOtherRoot(it, contentRoot, moduleEntity, rootToModules) } }
        .toSet()
      if (clashingPaths.isEmpty()) continue

      logger.info("Removing source roots clashing with other modules from '${moduleEntity.name}': $clashingPaths")
      projectStorage.modifyContentRootEntity(contentRootEntity) {
        sourceRoots = sourceRoots.filterNot { it.url.toPath() in clashingPaths }
      }
    }
  }
}

/**
 * Walk from [moduleSrc] up to [moduleContentRoot] (inclusive).
 * Return `true` if any intermediate path is a content root owned by a module other than [currentModule].
 */
private fun clashesWithOtherRoot(
  moduleSrc: Path,
  moduleContentRoot: Path,
  currentModule: ModuleEntity,
  rootToModules: Map<Path, Set<ModuleEntity>>,
): Boolean {
  var current = moduleSrc
  do {
    val owners = rootToModules[current]
    if (owners != null && owners.any { it != currentModule }) return true
    current = current.parent ?: break
  }
  while (current.startsWith(moduleContentRoot))
  return false
}

private fun renameSameModuleAndMoveSources(syncStorage: EntityStorage, projectStorage: MutableEntityStorage) {
  // TODO: fix O(N^2)
  for (syncModuleEntity in syncStorage.entities<ModuleEntity>()) {
    for (projectModuleEntity in projectStorage.entities<ModuleEntity>()) {
      if (ModuleAnchor(syncModuleEntity).sameAs(ModuleAnchor(projectModuleEntity))) {
        projectStorage.modifyModuleEntity(projectModuleEntity) {
          logger.debug {
              buildString {
                appendLine("modify module entity:")
                appendLine("name: ${syncModuleEntity.name}")
                appendLine("source: ${syncModuleEntity.entitySource}")
              }
          }
          name = syncModuleEntity.name
          entitySource = syncModuleEntity.entitySource
        }
        for (projectRootEntity in projectModuleEntity.contentRoots.toList()) {
          projectStorage.modifyContentRootEntity(projectRootEntity) {
            entitySource = syncModuleEntity.entitySource
            logger.debug {
              buildString {
                appendLine("modify content root:")
                appendLine("root: ${projectRootEntity.url}")
                appendLine("source: ${syncModuleEntity.entitySource}")
              }
            }
          }
        }
      }
    }
  }
}

private fun relocateFacetAndSdk(syncStorage: MutableEntityStorage, projectStorage: MutableEntityStorage) {
  val projectContentRootsByUrl = projectStorage.entities<ContentRootEntity>().associateBy { it.url.url }
  for (syncModuleEntity in syncStorage.entities<ModuleEntity>().toList()) {
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
    for (syncRootEntry in syncModuleEntity.contentRoots.toList()) {
      val projectRootEntity = projectContentRootsByUrl[syncRootEntry.url.url] ?: continue
      syncStorage.modifyContentRootEntity(syncRootEntry) {
        for (fixer in rootFixers) {
          fixer.addRoots(this, projectRootEntity)
        }
      }
    }
  }
}

private abstract class RootFixer<E : WorkspaceEntity, B : WorkspaceEntity.Builder<E>> {
  protected abstract fun getRoots(rootEntity: ContentRootEntity): List<E>
  protected abstract fun getRoots(rootEntity: ContentRootEntityBuilder): List<B>
  protected abstract fun setRoots(roots: List<B>, rootBuilder: ContentRootEntityBuilder)
  protected abstract fun getVirtualUrl(entity: B): VirtualFileUrl

  fun addRoots(to: ContentRootEntityBuilder, from: ContentRootEntity) {
    val dstRoots = getRoots(to)

    @Suppress("UNCHECKED_CAST")
    val newRoots = (getRoots(from).map { it.createEntityTreeCopy() as B } + dstRoots).distinctBy { getVirtualUrl(it).url }
    setRoots(newRoots, to)
  }
}

private object SourceRootFixer : RootFixer<SourceRootEntity, SourceRootEntityBuilder>() {
  override fun getRoots(rootEntity: ContentRootEntity): List<SourceRootEntity> = rootEntity.sourceRoots

  override fun getRoots(rootEntity: ContentRootEntityBuilder): List<SourceRootEntityBuilder> = rootEntity.sourceRoots

  override fun setRoots(
    roots: List<SourceRootEntityBuilder>,
    rootBuilder: ContentRootEntityBuilder,
  ) {
    rootBuilder.sourceRoots = roots
  }

  override fun getVirtualUrl(entity: SourceRootEntityBuilder): VirtualFileUrl = entity.url
}

private object ExcludeRootFixer : RootFixer<ExcludeUrlEntity, ExcludeUrlEntityBuilder>() {
  override fun getRoots(rootEntity: ContentRootEntity): List<ExcludeUrlEntity> = rootEntity.excludedUrls

  override fun getRoots(rootEntity: ContentRootEntityBuilder): List<ExcludeUrlEntityBuilder> = rootEntity.excludedUrls

  override fun setRoots(
    roots: List<ExcludeUrlEntityBuilder>,
    rootBuilder: ContentRootEntityBuilder,
  ) {
    rootBuilder.excludedUrls = roots
  }

  override fun getVirtualUrl(entity: ExcludeUrlEntityBuilder): VirtualFileUrl = entity.url
}

private val rootFixers = arrayOf(ExcludeRootFixer, SourceRootFixer)

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
          SourceRootEntity(srcRoot.toVirtualFileUrl(virtualFileUrlManager), JAVA_SOURCE_ROOT_TYPE, entitySource)
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
        externalSystem = PY_PROJECT_SYSTEM_ID.id
      }
    }
    moduleEntity.symbolicId
  }
  return@withContext storage.toSnapshot()
}

// For the time being mark them as java-sources to indicate that in the Project tool window
// Any other type isn't blue
private val JAVA_SOURCE_ROOT_TYPE: SourceRootTypeId = SourceRootTypeId("java-source")

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
  val moduleRoot = project.stateStore.directoryStorePath!!.toVirtualFileUrl(project.workspaceModel.getVirtualFileUrlManager())
  val externalSource = ExternalProjectSystemRegistry.getInstance()
    .getSourceById(PY_PROJECT_SYSTEM_ID.id)
  return LegacyBridgeJpsEntitySourceFactory.getInstance(project)
    .createEntitySourceForModule(moduleRoot, externalSource)
}
