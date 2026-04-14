package com.intellij.python.pyproject.model.internal.workspaceBridge

import com.intellij.configurationStore.saveSettings
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.JpsImportedEntitySource
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.ExternalSystemModuleOptionsEntity
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.jps.entities.ModuleTypeId
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootTypeId
import com.intellij.platform.workspace.jps.entities.exModuleOptions
import com.intellij.platform.workspace.jps.entities.modifyContentRootEntity
import com.intellij.platform.workspace.jps.entities.modifyExternalSystemModuleOptionsEntity
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.jps.entities.sdkId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
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

/** Collect all excluded folder paths from the workspace model. */
internal fun collectExcludedPaths(project: Project): Set<Path> {
  return project.workspaceModel.currentSnapshot.entities<ContentRootEntity>()
    .flatMap { cr -> cr.excludedUrls.asSequence().mapNotNull { it.url.toPath() } }
    .toSet()
}

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
    val currentSnapshot = project.workspaceModel.currentSnapshot

    // All module names (Python and non-Python) — module names must be unique across the project.
    val allModuleNames: Set<String> = currentSnapshot.entities<ModuleEntity>().map { it.name }.toSet()

    // Existing Python module names keyed by their content root directory.
    val existingPythonNames: Map<Path, String> = currentSnapshot.entities<ModuleEntity>()
      .filter { it.type == PYTHON_MODULE_ID }
      .mapNotNull { module ->
        val moduleRootPath = module.contentRoots.singleOrNull()?.url?.toPath()
        moduleRootPath?.let { it to module.name }
      }
      .toMap()

    val entries = generatePyProjectTomlEntries(files, existingPythonNames, allModuleNames)

    project.workspaceModel.update(PyProjectTomlBundle.message("action.PyProjectTomlSyncAction.description")) { projectStorage ->
      applyProjectModel(entries, project, projectStorage)
      ensureNoSrcIntersectsWithOtherRoots(projectStorage)
    }
    // Flush .iml files to disk to make changes visible for VCS and to prevent races with VFS.
    saveSettings(project)
  }
}

/**
 * Apply the desired project model described by [entries] directly to [projectStorage].
 * Renames are safe because the dedup reserves all current module names — renamed modules
 * always get genuinely new names that can't trigger the VFS cascade (PY-89055).
 */
private fun applyProjectModel(
  entries: Set<PyProjectTomlBasedEntryImpl>,
  project: Project,
  projectStorage: MutableEntityStorage,
) {
  val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
  val allModules = projectStorage.entities<ModuleEntity>().toList()
  val pyProjectTomlModules = allModules.filter { it.entitySource.isPythonEntity }

  val matched = matchEntriesToModules(entries, allModules, pyProjectTomlModules, virtualFileUrlManager)
  val newEntries = entries - matched.keys
  val orphanModules = pyProjectTomlModules - matched.values.toSet()

  logIfNeeded(projectStorage, "before apply")

  for (module in orphanModules) {
    deleteModule(module, projectStorage)
  }
  for ((entry, module) in matched) {
    updateModule(entry, module, project, projectStorage, virtualFileUrlManager)
  }
  for (entry in newEntries) {
    addNewModule(entry, project, projectStorage, virtualFileUrlManager)
  }

  logIfNeeded(projectStorage, "after apply")
}

/**
 * Match pyproject.toml entries to existing workspace modules in two passes.
 */
private fun matchEntriesToModules(
  entries: Set<PyProjectTomlBasedEntryImpl>,
  allModules: List<ModuleEntity>,
  pyProjectTomlModules: List<ModuleEntity>,
  virtualFileUrlManager: VirtualFileUrlManager,
): Map<PyProjectTomlBasedEntryImpl, ModuleEntity> {
  val matched = LinkedHashMap<PyProjectTomlBasedEntryImpl, ModuleEntity>()
  val claimedModules = mutableSetOf<ModuleEntity>()

  // First pass: match by location against Python-typed modules (adopts non-pyproject Python modules at the same root)
  // This is the primary and most reliable matching strategy.
  val pythonTypedModules = allModules.filter { it.type == PYTHON_MODULE_ID }
  val moduleAnchors = pythonTypedModules.associateWith { ModuleAnchor(it) }
  for (entry in entries) {
    val entryRootUrl = entry.root.toVirtualFileUrl(virtualFileUrlManager)
    val entryTomlDirUrl = entry.tomlFile.parent.toVirtualFileUrl(virtualFileUrlManager)
    val module = moduleAnchors.entries.firstOrNull { (m, anchor) ->
      m !in claimedModules && anchor.sameLocation(entryTomlDirUrl, entryRootUrl)
    }?.key
    if (module != null) {
      matched[entry] = module
      claimedModules.add(module)
    }
  }

  // Second pass: match remaining (location-unmatched) entries by name against unclaimed pyproject.toml-based modules.
  //
  // An unclaimed pyproject module here means its original pyproject.toml location no longer exists (directory
  // was renamed/moved). Matching by name lets us "carry over" the old module entity to the new location,
  // preserving user-configured state that would otherwise be lost: SDK, facets, manually added source/exclude roots, etc.
  //
  // Trade-off: if two unrelated pyproject.toml files happen to share a name and both change locations
  // simultaneously, the heuristic can cross-wire them. In practice this is rare.
  //
  // This heuristic is debatable — if it causes unexpected behavior or bugs, consider removing it entirely.
  for (entry in entries) {
    if (entry in matched) continue
    val module = pyProjectTomlModules.firstOrNull { m ->
      m !in claimedModules &&
      m.name.substringBefore('@') == entry.name.name.substringBefore('@')
    }
    if (module != null) {
      matched[entry] = module
      claimedModules.add(module)
    }
  }

  return matched
}

private fun deleteModule(
  module: ModuleEntity,
  projectStorage: MutableEntityStorage,
) {
  logger.debug { "Removing orphan Python module: ${module.name}" }

  // Preserve excluded folders by relocating them to the parent module.
  // When the module's content root itself is marked as excluded, that exclusion must not be lost on deletion.
  val relocations = module.contentRoots
    .filter { cr -> cr.excludedUrls.any { it.url == cr.url } }
    .mapNotNull { cr ->
      val moduleRootPath = cr.url.toPath()
      val parentContentRoot = projectStorage.entities<ContentRootEntity>()
        .filter { it.module != module && moduleRootPath.startsWith(it.url.toPath()) }
        .maxByOrNull { it.url.url.length }
      parentContentRoot
        ?.takeIf { p -> p.excludedUrls.none { it.url == cr.url } }
        ?.let { cr.url to it }
    }

  for ((excludeUrl, parentContentRoot) in relocations) {
    logger.debug { "Relocating excluded root '$excludeUrl' from '${module.name}' to '${parentContentRoot.module.name}'" }
    projectStorage.modifyContentRootEntity(parentContentRoot) {
      this.excludedUrls += ExcludeUrlEntity(excludeUrl, this.entitySource)
    }
  }

  projectStorage.removeEntity(module)
}

/** Update a matched module: rename + update properties + update/create child entities. */
private fun updateModule(
  entry: PyProjectTomlBasedEntryImpl,
  module: ModuleEntity,
  project: Project,
  projectStorage: MutableEntityStorage,
  virtualFileUrlManager: VirtualFileUrlManager,
) {
  val entitySource = if (module.entitySource.isPythonEntity) module.entitySource else createEntitySource(project)
  val entryRootUrl = entry.root.toVirtualFileUrl(virtualFileUrlManager)
  val tomlDirUrl = entry.tomlFile.parent.toVirtualFileUrl(virtualFileUrlManager)
  val desiredSourceRootUrls = entry.sourceRoots.map { it.toVirtualFileUrl(virtualFileUrlManager) }.toSet()
  val participatedTools = buildParticipatedToolsMap(entry)

  val existingContentRoot = module.contentRoots.firstOrNull { it.url == entryRootUrl }
  val existingPyProjectToml = module.pyProjectTomlEntity
  val existingExModuleOptions = module.exModuleOptions

  val desiredModuleDeps = entry.dependencies.map { it.name }.toSet()
  val existingModuleDeps = module.dependencies.filterIsInstance<ModuleDependency>().map { it.module.name }.toSet()
  val isModuleDependenciesChanged = existingModuleDeps != desiredModuleDeps

  // At most ONE modifyModuleEntity call per module to avoid "persistent id already exists" errors
  // and UI flicker from multiple change events.
  val needsModuleModify = module.name != entry.name.name
                          || module.entitySource != entitySource
                          || module.type != PYTHON_MODULE_ID
                          || existingContentRoot == null
                          || existingPyProjectToml == null
                          || existingExModuleOptions == null
                          || isModuleDependenciesChanged

  if (needsModuleModify) {
    if (module.name != entry.name.name) {
      logger.debug { "Renaming module '${module.name}' -> '${entry.name.name}'" }
    }

    projectStorage.modifyModuleEntity(module) {
      this.name = entry.name.name
      this.entitySource = entitySource
      this.type = PYTHON_MODULE_ID

      if (isModuleDependenciesChanged) {
        // Replacing `dependencies` wipes the SdkDependency item — save and restore it.
        val existingSdk = module.sdkId
        this.dependencies = mutableListOf<com.intellij.platform.workspace.jps.entities.ModuleDependencyItem>().apply {
          add(ModuleSourceDependency)
          for (depName in entry.dependencies) {
            add(ModuleDependency(ModuleId(depName.name), true, DependencyScope.COMPILE, false))
          }
        }
        this.sdkId = existingSdk
      }

      if (existingContentRoot == null) {
        this.contentRoots = listOf(ContentRootEntity(entryRootUrl, emptyList(), entitySource) {
          this.sourceRoots = desiredSourceRootUrls.map { url ->
            SourceRootEntity(url, JAVA_SOURCE_ROOT_TYPE, entitySource)
          }
        })
      }
      if (existingPyProjectToml == null) {
        this.pyProjectTomlEntity = PyProjectTomlWorkspaceEntity(participatedTools, tomlDirUrl, entitySource)
      }
      if (existingExModuleOptions == null) {
        this.exModuleOptions = ExternalSystemModuleOptionsEntity(entitySource) {
          this.externalSystem = PY_PROJECT_SYSTEM_ID.id
        }
      }
    }
  }

  existingContentRoot?.let {
    it to it.sourceRoots.map { sr -> sr.url.url }.toSet()
  }?.takeIf { (cr, existingUrls) ->
    cr.entitySource != entitySource || desiredSourceRootUrls.any { it.url !in existingUrls }
  }?.let { (cr, existingUrls) ->
    projectStorage.modifyContentRootEntity(cr) {
      this.entitySource = entitySource
      val newSourceRoots = desiredSourceRootUrls
        .filter { d -> d.url !in existingUrls }
        .map { url -> SourceRootEntity(url, JAVA_SOURCE_ROOT_TYPE, entitySource) }
      this.sourceRoots = (sourceRoots.map { sr ->
        if (sr.entitySource != entitySource) SourceRootEntity(sr.url, sr.rootTypeId, entitySource) else sr
      } + newSourceRoots).distinctBy { it.url.url }
      this.excludedUrls = excludedUrls.map { eu ->
        if (eu.entitySource != entitySource) ExcludeUrlEntity(eu.url, entitySource) else eu
      }
    }
  }

  existingPyProjectToml?.takeIf {
    it.participatedTools != participatedTools || it.dirWithToml != tomlDirUrl || it.entitySource != entitySource
  }?.let {
    projectStorage.modifyPyProjectTomlWorkspaceEntity(it) {
      this.participatedTools = participatedTools
      this.dirWithToml = tomlDirUrl
      this.entitySource = entitySource
    }
  }

  existingExModuleOptions?.takeIf {
    it.externalSystem != PY_PROJECT_SYSTEM_ID.id || it.entitySource != entitySource
  }?.let {
    projectStorage.modifyExternalSystemModuleOptionsEntity(it) {
      this.externalSystem = PY_PROJECT_SYSTEM_ID.id
      this.entitySource = entitySource
    }
  }
}

/** Add a new module for an entry that didn't match any existing module. */
private fun addNewModule(
  entry: PyProjectTomlBasedEntryImpl,
  project: Project,
  projectStorage: MutableEntityStorage,
  virtualFileUrlManager: VirtualFileUrlManager,
) {
  val entitySource = createEntitySource(project)
  val tomlDirUrl = entry.tomlFile.parent.toVirtualFileUrl(virtualFileUrlManager)
  val entryRootUrl = entry.root.toVirtualFileUrl(virtualFileUrlManager)
  val participatedTools = buildParticipatedToolsMap(entry)

  projectStorage addEntity ModuleEntity(entry.name.name, emptyList(), entitySource) {
    dependencies += ModuleSourceDependency
    for (depName in entry.dependencies) {
      dependencies += ModuleDependency(ModuleId(depName.name), true, DependencyScope.COMPILE, false)
    }
    contentRoots = listOf(ContentRootEntity(entryRootUrl, emptyList(), entitySource) {
      sourceRoots = entry.sourceRoots.map { srcRoot ->
        SourceRootEntity(srcRoot.toVirtualFileUrl(virtualFileUrlManager), JAVA_SOURCE_ROOT_TYPE, entitySource)
      }
    })
    type = PYTHON_MODULE_ID
    pyProjectTomlEntity = PyProjectTomlWorkspaceEntity(participatedTools, tomlDirUrl, entitySource)
    exModuleOptions = ExternalSystemModuleOptionsEntity(entitySource) {
      externalSystem = PY_PROJECT_SYSTEM_ID.id
    }
  }
}

private fun buildParticipatedToolsMap(entry: PyProjectTomlBasedEntryImpl): Map<ToolId, ModuleId?> {
  val participatedTools: MutableMap<ToolId, ModuleId?> =
    entry.relationsWithTools.associate { Pair(it.toolId, null) }.toMutableMap()
  for (relation in entry.relationsWithTools) {
    when (relation) {
      is PyProjectTomlToolRelation.SimpleRelation -> Unit
      is PyProjectTomlToolRelation.WorkspaceMember -> {
        participatedTools[relation.toolId] = ModuleId(relation.workspace.name)
      }
    }
  }
  return participatedTools
}


private fun logIfNeeded(projectStorage: MutableEntityStorage, title: String) {
  logger.debug {
    buildString {
      appendLine("WSM $title")
      for (moduleEntity in projectStorage.entities<ModuleEntity>()) {
        appendLine("${moduleEntity.name} (${moduleEntity.entitySource}) num of roots ${moduleEntity.contentRoots.size}")
      }
    }
  }
}

/**
 * No module should have source/exclude roots that clash with a content root of another module.
 * Clashing roots are relocated to the innermost owning child module instead of being dropped (PY-89073).
 */
private fun ensureNoSrcIntersectsWithOtherRoots(projectStorage: MutableEntityStorage) {
  val moduleEntities = projectStorage.entities<ModuleEntity>().toList()
  val rootToModules = HashMap<Path, MutableSet<ModuleEntity>>()
  for (moduleEntity in moduleEntities) {
    for (contentRoot in moduleEntity.contentRoots) {
      rootToModules.getOrPut(contentRoot.url.toPath()) { mutableSetOf() }.add(moduleEntity)
    }
  }

  data class SourceRelocation(val sourceRoot: SourceRootEntity, val from: ContentRootEntity, val to: ModuleEntity)
  data class ExcludeRelocation(val excludeUrl: ExcludeUrlEntity, val from: ContentRootEntity, val to: ModuleEntity)

  val sourceRelocations = ArrayList<SourceRelocation>()
  val excludeRelocations = ArrayList<ExcludeRelocation>()

  for (moduleEntity in moduleEntities) {
    for (contentRootEntity in moduleEntity.contentRoots.toList()) {
      val contentRoot = contentRootEntity.url.toPath()

      for (sourceRoot in contentRootEntity.sourceRoots) {
        val target = findInnermostOwner(sourceRoot.url.toPath(), contentRoot, moduleEntity, rootToModules)
        if (target != null) {
          sourceRelocations.add(SourceRelocation(sourceRoot, contentRootEntity, target))
        }
      }

      for (excludeUrl in contentRootEntity.excludedUrls) {
        val target = findInnermostOwner(excludeUrl.url.toPath(), contentRoot, moduleEntity, rootToModules)
        if (target != null) {
          excludeRelocations.add(ExcludeRelocation(excludeUrl, contentRootEntity, target))
        }
      }
    }
  }

  if (sourceRelocations.isEmpty() && excludeRelocations.isEmpty()) return

  // Remove clashing roots from their current content roots
  val sourceRemovals = sourceRelocations.groupBy({ it.from }, { it.sourceRoot.url.toPath() })
  val excludeRemovals = excludeRelocations.groupBy({ it.from }, { it.excludeUrl.url.toPath() })
  val allAffectedContentRoots = sourceRemovals.keys + excludeRemovals.keys
  for (contentRootEntity in allAffectedContentRoots) {
    val srcPaths = sourceRemovals[contentRootEntity]?.toSet() ?: emptySet()
    val exclPaths = excludeRemovals[contentRootEntity]?.toSet() ?: emptySet()
    logger.info("Relocating roots from '${contentRootEntity.module.name}': sources=$srcPaths, excludes=$exclPaths")
    projectStorage.modifyContentRootEntity(contentRootEntity) {
      if (srcPaths.isNotEmpty()) {
        sourceRoots = sourceRoots.filterNot { it.url.toPath() in srcPaths }
      }
      if (exclPaths.isNotEmpty()) {
        excludedUrls = excludedUrls.filterNot { it.url.toPath() in exclPaths }
      }
    }
  }

  // Add roots to the target child module's content root
  val sourceAdditions = sourceRelocations.groupBy({ it.to }, { it.sourceRoot })
  val excludeAdditions = excludeRelocations.groupBy({ it.to }, { it.excludeUrl })
  val allTargetModules = sourceAdditions.keys + excludeAdditions.keys
  for (targetModule in allTargetModules) {
    val targetContentRoot = targetModule.contentRoots.firstOrNull() ?: continue

    val newSources = sourceAdditions[targetModule]?.let { roots ->
      val existingUrls = targetContentRoot.sourceRoots.map { it.url.url }.toSet()
      roots.filter { it.url.url !in existingUrls }
    } ?: emptyList()

    val newExcludes = excludeAdditions[targetModule]?.let { roots ->
      val existingUrls = targetContentRoot.excludedUrls.map { it.url.url }.toSet()
      roots.filter { it.url.url !in existingUrls }
    } ?: emptyList()

    if (newSources.isEmpty() && newExcludes.isEmpty()) continue
    projectStorage.modifyContentRootEntity(targetContentRoot) {
      if (newSources.isNotEmpty()) {
        this.sourceRoots += newSources.map { SourceRootEntity(it.url, it.rootTypeId, this.entitySource) }
      }
      if (newExcludes.isNotEmpty()) {
        this.excludedUrls += newExcludes.map { ExcludeUrlEntity(it.url, this.entitySource) }
      }
    }
  }
}

/**
 * Walk from [sourcePath] up to [moduleContentRoot] (exclusive of the module's own root).
 * Return the innermost module that owns a content root on this path (the child module the source root belongs to),
 * or null if no other module's content root is encountered.
 */
private fun findInnermostOwner(
  sourcePath: Path,
  moduleContentRoot: Path,
  currentModule: ModuleEntity,
  rootToModules: Map<Path, Set<ModuleEntity>>,
): ModuleEntity? {
  var current = sourcePath
  do {
    val owners = rootToModules[current]
    if (owners != null) {
      val otherOwner = owners.firstOrNull { it != currentModule }
      if (otherOwner != null) return otherOwner
    }
    current = current.parent ?: break
  }
  while (current.startsWith(moduleContentRoot))
  return null
}

private suspend fun generatePyProjectTomlEntries(
  fsInfo: FSWalkInfoWithToml,
  existingPythonNames: Map<Path, String>,
  allModuleNames: Set<String>,
): Set<PyProjectTomlBasedEntryImpl> = withContext(Dispatchers.Default) {
  val tools = Tool.EP.extensionList
  val rawEntries = parseRawEntries(fsInfo, tools)
  val entries = assignNames(rawEntries, existingPythonNames, allModuleNames)
  resolveDependencies(entries, tools)
  return@withContext entries.toSet()
}

private data class RawEntry(
  val tomlFile: Path,
  val root: Directory,
  val naturalName: String,
  val participatedTools: MutableSet<ToolId>,
  val toml: PyProjectToml,
  val sourceRoots: Set<Directory>,
  val relationsWithTools: MutableSet<PyProjectTomlToolRelation>,
)

/** Parse pyproject.toml files into raw entries with natural names (no dedup). */
private suspend fun parseRawEntries(fsInfo: FSWalkInfoWithToml, tools: List<Tool>): List<RawEntry> {
  val rawEntries = ArrayList<RawEntry>()
  for ((tomlFile, toml) in fsInfo.tomlFiles.entries.sortedBy { it.key }) {
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
    val sourceRootsAndTools = tools.flatMap { tool -> tool.getSrcRoots(toml.toml, root).map { Pair(tool, it) } }.toSet()
    val sourceRoots = sourceRootsAndTools.map { it.second }.toSet() + findSrc(root)
    participatedTools.addAll(sourceRootsAndTools.map { it.first.id })
    if (participatedTools.isEmpty()) {
      for (tool in tools) {
        if (toml.toml.contains("tool.${tool.id.id}")) {
          participatedTools.add(tool.id)
        }
      }
    }
    if (participatedTools.isEmpty()) {
      toml.toml.getString("build-system.build-backend")?.let { buildBackend ->
        tools.firstOrNull { it.id.id in buildBackend }?.let { buildTool ->
          participatedTools.add(buildTool.id)
        }
      }
    }

    val relationsWithTools: MutableSet<PyProjectTomlToolRelation> = participatedTools.mapTo(mutableSetOf()) {
      PyProjectTomlToolRelation.SimpleRelation(it)
    }
    rawEntries.add(RawEntry(tomlFile, root, projectNameAsString, participatedTools, toml, sourceRoots, relationsWithTools))
  }
  return rawEntries
}

/**
 * Assign final module names to raw entries.
 *
 * All current module names (Python + non-Python, including orphans) are reserved to prevent
 * renames from targeting names that currently exist — this avoids the VFS cascade where
 * bridge renames .iml → FileReferenceInWorkspaceEntityUpdater → SymbolicIdAlreadyExistsException.
 *
 * - Entries whose existing module name matches (clean name or @N variant with contested base) → keep it.
 * - Entries with a unique natural name not in reserved set → use it (enables renames to fresh names).
 * - Everything else (duplicates, clashes) → generate @N suffix.
 */
private fun assignNames(
  rawEntries: List<RawEntry>,
  existingPythonNames: Map<Path, String>,
  allModuleNames: Set<String>,
): List<PyProjectTomlBasedEntryImpl> {
  val usedNames = allModuleNames.toMutableSet()

  val naturalNameCounts = rawEntries.groupingBy { it.naturalName }.eachCount()

  val entries = ArrayList<PyProjectTomlBasedEntryImpl>()

  // First pass: preserve existing names for modules whose name already matches their natural name.
  // For @N suffixed names, only preserve if the clean name is still contested (duplicated or taken).
  val needsAssignment = ArrayList<RawEntry>()
  for (rawEntry in rawEntries) {
    val existingName = existingPythonNames[rawEntry.root]
    val isCleanMatch = existingName == rawEntry.naturalName
    val isSuffixMatch = existingName != null && existingName.startsWith("${rawEntry.naturalName}@")
    val cleanNameContested = (naturalNameCounts[rawEntry.naturalName] ?: 0) > 1 || rawEntry.naturalName in usedNames
    if (isCleanMatch || (isSuffixMatch && cleanNameContested)) {
      entries.add(PyProjectTomlBasedEntryImpl(
        rawEntry.tomlFile, rawEntry.relationsWithTools, rawEntry.toml,
        ProjectName(existingName), rawEntry.root, mutableSetOf(), rawEntry.sourceRoots,
      ))
    }
    else {
      needsAssignment.add(rawEntry)
    }
  }

  // Second pass: assign names for remaining entries (new modules, or renamed modules).
  for (rawEntry in needsAssignment) {
    var finalName = rawEntry.naturalName
    if (finalName in usedNames) {
      val baseName = finalName
      var counter = 1
      do {
        finalName = "$baseName@$counter"
        counter++
      }
      while (finalName in usedNames)
    }
    usedNames.add(finalName)
    entries += PyProjectTomlBasedEntryImpl(
      tomlFile = rawEntry.tomlFile,
      relationsWithTools = rawEntry.relationsWithTools,
      pyProjectToml = rawEntry.toml,
      name = ProjectName(finalName),
      root = rawEntry.root,
      dependencies = mutableSetOf(),
      sourceRoots = rawEntry.sourceRoots,
    )
  }

  return entries
}

/** Resolve inter-module dependencies and workspace membership from tools. */
private suspend fun resolveDependencies(entries: List<PyProjectTomlBasedEntryImpl>, tools: List<Tool>) {
  val entriesByName = entries.associateBy { it.name }
  val namesByDir = entries.associate { Pair(it.root, it.name) }
  val allNames = entriesByName.keys
  var dependencies = getDependenciesFromToml(entriesByName, namesByDir, tools.flatMap { it.getTomlDependencySpecifications() })
  for (tool in tools) {
    val toolSpecificInfo = tool.getProjectStructure(entriesByName, namesByDir)
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
}

private suspend fun Iterable<Tool>.getNameFromEP(projectToml: PyProjectToml): Pair<Tool, @NlsSafe String>? =
  withContext(Dispatchers.Default) {
    firstNotNullOfOrNull { tool -> tool.getProjectName(projectToml.toml)?.let { Pair(tool, it) } }
  }

// For the time being mark them as java-sources to indicate that in the Project tool window
// Any other type isn't blue
private val JAVA_SOURCE_ROOT_TYPE: SourceRootTypeId = SourceRootTypeId("java-source")

private class PyProjectTomlBasedEntryImpl(
  val tomlFile: Path,
  val relationsWithTools: MutableSet<PyProjectTomlToolRelation>,
  override val pyProjectToml: PyProjectToml,
  val name: ProjectName,
  override val root: Directory,
  val dependencies: MutableSet<ProjectName>,
  val sourceRoots: Set<Directory>,
) : PyProjectTomlProject {
  override fun equals(other: Any?): Boolean = other is PyProjectTomlBasedEntryImpl && tomlFile == other.tomlFile
  override fun hashCode(): Int = tomlFile.hashCode()
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

private val EntitySource.isPythonEntity: Boolean get() = (this as? JpsImportedEntitySource)?.externalSystemId == PY_PROJECT_SYSTEM_ID.id

/**
 * Anchor for matching entries to existing modules by location.
 */
private class ModuleAnchor(moduleEntity: ModuleEntity) {
  private val dirWithToml = moduleEntity.pyProjectTomlEntity?.dirWithToml
  private val theOnlyContentRoot = moduleEntity.contentRoots.let { if (it.size == 1) it[0] else null }

  fun getLocation(): VirtualFileUrl? = dirWithToml ?: theOnlyContentRoot?.url

  fun sameLocation(entryTomlDirUrl: VirtualFileUrl, entryRootUrl: VirtualFileUrl): Boolean =
    dirWithToml == entryTomlDirUrl || theOnlyContentRoot?.url == entryRootUrl
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
