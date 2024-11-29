// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.UnloadedModulesListStorage
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.workspace.jps.JpsImportedEntitySource
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.jps.serialization.impl.FileInDirectorySourceNames
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.util.ExceptionUtil
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.idea.maven.importing.*
import org.jetbrains.idea.maven.importing.tree.MavenModuleImportContext
import org.jetbrains.idea.maven.importing.tree.MavenProjectImportContextProvider
import org.jetbrains.idea.maven.importing.tree.MavenTreeModuleImportData
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.statistics.MavenImportCollector
import org.jetbrains.idea.maven.telemetry.tracer
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.jps.model.serialization.SerializationConstants
import java.nio.file.Path
import java.util.function.Function
import kotlin.coroutines.cancellation.CancellationException

internal val AFTER_IMPORT_CONFIGURATOR_EP: ExtensionPointName<MavenAfterImportConfigurator> = ExtensionPointName.create(
  "org.jetbrains.idea.maven.importing.afterImportConfigurator")

@TestOnly
@Internal
var WORKSPACE_IMPORTER_SKIP_FAST_APPLY_ATTEMPTS_ONCE = false

internal open class WorkspaceProjectImporter(
  protected val myProjectsTree: MavenProjectsTree,
  protected val projectsToImportWithChanges: Map<MavenProject, MavenProjectChanges>,
  protected val myImportingSettings: MavenImportingSettings,
  protected val myModifiableModelsProvider: IdeModifiableModelsProvider,
  protected val myProject: Project,
) : MavenProjectImporter {
  private val virtualFileUrlManager = WorkspaceModel.getInstance(myProject).getVirtualFileUrlManager()
  private val createdModulesList = java.util.ArrayList<Module>()

  protected open fun workspaceConfigurators(): List<MavenWorkspaceConfigurator> {
    return MavenWorkspaceConfigurator.EXTENSION_POINT_NAME.extensionList
  }

  override fun importProject(): List<MavenProjectsProcessorTask> {
    MavenLog.LOG.info("Importing Maven project using Workspace API")

    val migratedToExternalStorage = migrateToExternalStorageIfNeeded()

    val storageBeforeImport = WorkspaceModel.getInstance(myProject).currentSnapshot

    val projectChangesInfo = tracer.spanBuilder("collectProjectChanges").use {
      collectProjectChanges(storageBeforeImport, projectsToImportWithChanges, migratedToExternalStorage)
    }

    if (!projectChangesInfo.hasChanges) return emptyList()

    val postTasks = ArrayList<MavenProjectsProcessorTask>()
    val stats = WorkspaceImportStats.start(myProject)

    val allProjectsToChanges = projectChangesInfo.allProjectsToChanges
    val externalSystemModuleEntities = storageBeforeImport.entities(ExternalSystemModuleOptionsEntity::class.java)
    val mavenProjectToModuleName = buildModuleNameMap(externalSystemModuleEntities, allProjectsToChanges)

    val builder = MutableEntityStorage.create()
    builder.addEntity(MavenProjectsTreeSettingsEntity(projectChangesInfo.projectFilePaths, MavenProjectsTreeEntitySource))

    val contextData = UserDataHolderBase()

    val projectsWithModuleEntities = stats.recordPhase(MavenImportCollector.WORKSPACE_POPULATE_PHASE) {
      tracer.spanBuilder("populateWorkspace").use {
        importModules(storageBeforeImport, builder, allProjectsToChanges, mavenProjectToModuleName, contextData, stats).also { projectWithModules ->
          tracer.spanBuilder("beforeModelApplied").use {
            beforeModelApplied(projectWithModules, builder, contextData, stats)
          }
        }
      }
    }
    val appliedProjectsWithModules = stats.recordPhase(MavenImportCollector.WORKSPACE_COMMIT_PHASE) {
      tracer.spanBuilder("commitWorkspace").use {
        commitModulesToWorkspaceModel(projectsWithModuleEntities, builder, contextData, stats)
      }
    }

    stats.recordPhase(MavenImportCollector.WORKSPACE_LEGACY_IMPORTERS_PHASE) { activity ->
      tracer.spanBuilder("configLegacyFacets").use {
        configLegacyFacets(appliedProjectsWithModules, mavenProjectToModuleName, postTasks, activity)
      }
    }

    MavenProjectImporterUtil.scheduleRefreshResolvedArtifacts(postTasks, projectChangesInfo.changedProjectsOnly)

    createdModulesList.addAll(appliedProjectsWithModules.flatMap { it.modules.asSequence().map { it.module } })

    stats.finish(numberOfModules = projectsWithModuleEntities.sumOf { it.modules.size })

    addAfterImportTask(postTasks, contextData, appliedProjectsWithModules)

    return postTasks

  }

  protected open fun addAfterImportTask(
    postTasks: ArrayList<MavenProjectsProcessorTask>,
    contextData: UserDataHolderBase,
    appliedProjectsWithModules: List<MavenProjectWithModulesData<Module>>,
  ) {
    postTasks.add(AfterImportConfiguratorsTask(contextData, appliedProjectsWithModules))
  }

  private fun migrateToExternalStorageIfNeeded(): Boolean {
    var migratedToExternalStorage = false
    val externalStorageManager = ExternalStorageConfigurationManager.getInstance(myProject)
    if (!externalStorageManager.isEnabled) {
      ExternalProjectsManagerImpl.getInstance(myProject).setStoreExternally(true)
      migratedToExternalStorage = true

      if (!externalStorageManager.isEnabled) {
        MavenLog.LOG.error(
          "Can't migrate the project to external project files storage: ExternalStorageConfigurationManager.isEnabled=false")
      }
      else if (!myProject.isExternalStorageEnabled) {
        MavenLog.LOG.warn("Can't migrate the project to external project files storage: Project.isExternalStorageEnabled=false")
      }
      else {
        MavenLog.LOG.info("Project has been migrated to external project files storage")
      }
    }
    return migratedToExternalStorage
  }

  private data class ProjectChangesInfo(val hasChanges: Boolean, val allProjectsToChanges: Map<MavenProject, MavenProjectChanges>) {
    val projectFilePaths: List<String> get() = allProjectsToChanges.keys.map { it.path }
    val changedProjectsOnly: Iterable<MavenProject>
      get() = allProjectsToChanges
        .asSequence()
        .filter { (_, changes) -> changes.hasChanges() }
        .map { (mavenProject, _) -> mavenProject }
        .asIterable()
  }

  private fun collectProjectChanges(
    storageBeforeImport: EntityStorage,
    originalProjectsChanges: Map<MavenProject, MavenProjectChanges>,
    migratedToExternalStorage: Boolean,
  ): ProjectChangesInfo {
    val mavenProjectsTreeSettingsEntity = storageBeforeImport.entities(MavenProjectsTreeSettingsEntity::class.java).firstOrNull()
    val projectFilesFromPreviousImport = mavenProjectsTreeSettingsEntity?.importedFilePaths?.toSet() ?: setOf()

    val allProjects = myProjectsTree.projects
      .filter { !myProjectsTree.isIgnored(it) }

    // if a pom was ignored or unignored, we must update all the modules, because they might have a module dependency on it
    // if it was ignored, module dependencies should be replaced with library dependencies and vice versa
    val projectsChanged = !sameProjects(projectFilesFromPreviousImport, allProjects)

    val allProjectsToChanges: Map<MavenProject, MavenProjectChanges> = allProjects.associateWith {
      if (projectsChanged) {
        MavenProjectChanges.ALL
      }
      else {
        val newProjectToImport = it.path !in projectFilesFromPreviousImport
        if (newProjectToImport) MavenProjectChanges.ALL else originalProjectsChanges.getOrDefault(it, MavenProjectChanges.NONE)
      }
    }

    val hasChanges = allProjectsToChanges.values.any { it.hasChanges() } || migratedToExternalStorage

    return ProjectChangesInfo(hasChanges, allProjectsToChanges)
  }

  private fun sameProjects(
    projectFilesFromPreviousImport: Set<String>,
    allProjects: List<MavenProject>,
  ) = allProjects.size == projectFilesFromPreviousImport.size &&
      allProjects.all { projectFilesFromPreviousImport.contains(it.path) }


  private fun getExistingModuleNames(externalSystemModuleEntities: Sequence<ExternalSystemModuleOptionsEntity>): Map<VirtualFile, String> {
    val keepExistingModuleNames = Registry.`is`("maven.import.keep.existing.module.names")
    if (!keepExistingModuleNames) return mapOf()

    // in case of compound modules, module, module.main and module.test are all mapped to the same file; module must be returned
    fun selectModuleEntity(externalSystemModuleEntities: List<ExternalSystemModuleOptionsEntity>): ExternalSystemModuleOptionsEntity {
      for (entity in externalSystemModuleEntities) {
        if (entity.externalSystemModuleType == StandardMavenModuleType.COMPOUND_MODULE.toString()) return entity
      }
      return externalSystemModuleEntities[0]
    }

    return externalSystemModuleEntities.filter { it.externalSystem == SerializationConstants.MAVEN_EXTERNAL_SOURCE_ID }
      .filter { it.linkedProjectId != null }
      .groupBy { LocalFileSystem.getInstance().findFileByPath(it.linkedProjectId!!) }
      .filterKeys { it != null }
      .mapKeys { it.key!! }
      .mapValues { selectModuleEntity(it.value).module.name }
  }

  private fun buildModuleNameMap(
    externalSystemModuleEntities: Sequence<ExternalSystemModuleOptionsEntity>,
    projectToImport: Map<MavenProject, MavenProjectChanges>,
  ): Map<MavenProject, String> {
    return MavenModuleNameMapper.mapModuleNames(myProjectsTree, projectToImport.keys, getExistingModuleNames(externalSystemModuleEntities))
  }

  private fun importModules(
    storageBeforeImport: EntityStorage,
    builder: MutableEntityStorage,
    projectsToImport: Map<MavenProject, MavenProjectChanges>,
    mavenProjectToModuleName: Map<MavenProject, String>,
    contextData: UserDataHolderBase,
    stats: WorkspaceImportStats,
  ): List<MavenProjectWithModulesData<ModuleEntity>> {
    val context = MavenProjectImportContextProvider(myProject, myProjectsTree, myImportingSettings,
                                                    mavenProjectToModuleName).getContext(projectsToImport)

    val entitySourceNamesBeforeImport = FileInDirectorySourceNames.from(storageBeforeImport)
    val folderImportingContext = WorkspaceFolderImporter.FolderImportingContext()

    class PartialModulesData(
      val changes: MavenProjectChanges,
      val modules: MutableList<ModuleWithTypeData<ModuleEntity>>,
    )

    val projectToModulesData = mutableMapOf<MavenProject, PartialModulesData>()
    val unloadedModulesNameHolder = UnloadedModulesListStorage.getInstance(myProject).unloadedModuleNameHolder

    for (importData in sortProjectsToImportByPrecedence(context)) {
      if (unloadedModulesNameHolder.isUnloaded(importData.moduleData.moduleName)) continue

      val moduleEntity = WorkspaceModuleImporter(myProject,
                                                 storageBeforeImport,
                                                 importData,
                                                 virtualFileUrlManager,
                                                 builder,
                                                 entitySourceNamesBeforeImport,
                                                 myImportingSettings,
                                                 folderImportingContext,
                                                 stats,
                                                 workspaceConfigurators()).importModule()

      val partialData = projectToModulesData.computeIfAbsent(importData.mavenProject, Function {
        PartialModulesData(importData.changes, mutableListOf())
      })
      partialData.modules.add(ModuleWithTypeData(moduleEntity, importData.moduleData.type))
    }

    val result = projectToModulesData.map { (mavenProject, partialData) ->
      MavenProjectWithModulesData(mavenProject, partialData.changes, partialData.modules)
    }

    tracer.spanBuilder("configureModules").use { configureModules(result, builder, contextData, stats) }
    return result
  }

  private fun sortProjectsToImportByPrecedence(context: MavenModuleImportContext): List<MavenTreeModuleImportData> {
    // We need to order the projects to import folders correctly:
    //   in case of overlapping root/source folders in several projects,
    //   we register them only once for the first project in the list

    val comparator =
      // order by file structure
      compareBy<MavenTreeModuleImportData> { it.mavenProject.directory }
        // if both projects reside in the same folder, then:

        // 'project' before 'project.main'/'project.test'
        .then(compareBy { MavenImportUtil.isMainOrTestSubmodule(it.moduleData.moduleName) })

        // '.main' before '.test'
        .then(compareBy { !MavenImportUtil.isMainModule(it.moduleData.moduleName) })

        // 'pom.*' files before custom named files (e.g. 'custom.xml')
        .then(compareBy { !FileUtil.namesEqual("pom", it.mavenProject.file.nameWithoutExtension) })

        // stabilize order by file name
        .thenComparing { a, b -> FileUtil.comparePaths(a.mavenProject.file.name, b.mavenProject.file.name) }

    return context.allModules.sortedWith(comparator)
  }

  private fun commitModulesToWorkspaceModel(
    mavenProjectsWithModules: List<MavenProjectWithModulesData<ModuleEntity>>,
    newStorage: MutableEntityStorage,
    contextData: UserDataHolderBase,
    stats: WorkspaceImportStats,
  ): List<MavenProjectWithModulesData<Module>> {
    val appliedModulesResult = mutableListOf<MavenProjectWithModulesData<Module>>()
    updateProjectModelFastOrSlow(myProject, stats,
                                 { snapshot -> applyToCurrentStorage(mavenProjectsWithModules, snapshot, newStorage) },
                                 { applied ->
                                   mapEntitiesToModulesAndRunAfterModelApplied(applied,
                                                                               mavenProjectsWithModules,
                                                                               appliedModulesResult,
                                                                               contextData,
                                                                               stats)
                                 })
    return appliedModulesResult
  }

  private fun applyToCurrentStorage(
    mavenProjectsWithModules: List<MavenProjectWithModulesData<ModuleEntity>>,
    currentStorage: MutableEntityStorage,
    newStorage: MutableEntityStorage,
  ) {
    // remove modules which should be replaced with Maven modules, in order to clean them from pre-existing sources, dependencies etc.
    // It's needed since otherwise 'replaceBySource' will merge pre-existing Module content with imported module content, resulting in
    // unexpected module configuration.
    val importedModuleNames by lazy {
      mavenProjectsWithModules.flatMapTo(mutableSetOf()) { it.modules.asSequence().map { it.module.name } }
    }

    // also remove non-Maven modules that has clashing content roots, otherwise we might end up with a situation:
    //  * A user opens a project with existing non-maven module 'A', with a single content root(==project root), and a pom.xml in the root.
    //  * The user asks the IDE to import pom.xml artifactId 'B'.
    //  * the IDE creates module 'B' along with a non-maven module 'A', both pointing at the same content root.
    //  -> IDE is confused - which module to use to resolve project files?.
    //  -> User thinks that either resolve or import is broken.
    val importedContentRootUrlsToModule by lazy {
      mavenProjectsWithModules
        .flatMapTo(mutableSetOf()) { it.modules.asSequence().flatMap { it.module.contentRoots.asSequence() }.map { it.url to it.module.name } }.toMap()
    }
    val modulesWithDuplicatingRoots: MutableSet<String> = mutableSetOf()

    // Here we detect content roots with URLs same to the ones that will be imported right now.
    //   If the root is in different module, remove content roots and the module if it remains empty. See the explanation above
    //   If this is the root from the module with the same name, save the name of the module to move source roots and excludes
    //     to the new content root
    currentStorage
      .entities(ModuleEntity::class.java)
      .filterNot { isMavenEntity(it.entitySource) }
      .filter { existingModule ->
        var removedSomeRoots = false
        existingModule.contentRoots.forEach { existingContentRoot ->
          val moduleToImport = importedContentRootUrlsToModule[existingContentRoot.url] ?: return@forEach
          if (moduleToImport == existingModule.name) {
            modulesWithDuplicatingRoots += moduleToImport
          }
          else {
            currentStorage.removeEntity(existingContentRoot)
            removedSomeRoots = true
          }
        }
        return@filter removedSomeRoots
      }
      // Cleanup modules if they remain without content roots at all
      .filter { it.contentRoots.isEmpty() }
      .forEach { currentStorage.removeEntity(it) }

    WorkspaceChangesRetentionUtil.retainManualChanges(myProject, currentStorage, newStorage)

    currentStorage.replaceBySource({ isMavenEntity(it) }, newStorage)

    // Now we have some modules with duplicating content roots. One content root existed before and another one exported from maven.
    //   We need to move source roots and excludes from existing content roots to the exported content roots and remove (obsolete) existing.
    modulesWithDuplicatingRoots.asSequence()
      .map { ModuleId(it) }
      .mapNotNull { currentStorage.resolve(it) }
      .forEach { moduleEntity ->
        val urlMap = moduleEntity.contentRoots.groupBy { it.url }
        urlMap.forEach internal@{ (url, entities) ->
          if (entities.size == 1) return@internal
          val to = entities.firstOrNull { isMavenEntity(it.entitySource) }
          val from = entities.firstOrNull { !isMavenEntity(it.entitySource) }

          // Process unexpected case. We expect exactly two roots, one imported and one not.
          //   Leave a single root if the expectation was not met
          if (entities.size != 2 || from == null || to == null) {
            entities.drop(1).forEach { currentStorage.removeEntity(it) }
            LOG.error("Unexpected state. We've got ${entities.size} similar content roots pointing to $url")
            return@internal
          }

          // Move source root if it doesn't exist already
          from.sourceRoots.forEach {
            if (to.sourceRoots.none { root -> root.url == it.url }) {
              currentStorage.modifySourceRootEntity(it) sourceRoot@{
                currentStorage.modifyContentRootEntity(to) contentRoot@{
                  this@sourceRoot.contentRoot = this@contentRoot
                }
              }
            }
          }

          // Move exclude if it doesn't exist already
          from.excludedUrls.forEach {
            if (to.excludedUrls.none { root -> root.url == it.url }) {
              currentStorage.modifyExcludeUrlEntity(it) sourceRoot@{
                currentStorage.modifyContentRootEntity(to) contentRoot@{
                  this@sourceRoot.contentRoot = this@contentRoot
                }
              }
            }
          }

          // Remove old content root
          currentStorage.removeEntity(from)
        }
      }
  }

  private fun mapEntitiesToModulesAndRunAfterModelApplied(
    appliedStorage: EntityStorage,
    mavenProjectsWithModules: List<MavenProjectWithModulesData<ModuleEntity>>,
    result: MutableList<MavenProjectWithModulesData<Module>>,
    contextData: UserDataHolderBase,
    stats: WorkspaceImportStats,
  ) {
    for (each in mavenProjectsWithModules) {
      val appliedModules = each.modules.mapNotNull<ModuleWithTypeData<ModuleEntity>, ModuleWithTypeData<Module>> {
        val originalEntity = it.module
        val appliedEntity = appliedStorage.resolve(originalEntity.symbolicId) ?: return@mapNotNull null
        val module = appliedEntity.findModule(appliedStorage) ?: return@mapNotNull null
        ModuleWithTypeData(module, it.type)
      }

      if (appliedModules.isNotEmpty()) {
        result.add(MavenProjectWithModulesData(each.mavenProject, each.changes, appliedModules))
      }
    }

    afterModelApplied(result, appliedStorage, contextData, stats)
  }

  private fun configureModules(
    projectsWithModules: List<MavenWorkspaceConfigurator.MavenProjectWithModules<ModuleEntity>>,
    builder: MutableEntityStorage,
    contextDataHolder: UserDataHolderBase,
    stats: WorkspaceImportStats,
  ) {
    val context = object : MavenWorkspaceConfigurator.MutableMavenProjectContext, UserDataHolderEx by contextDataHolder {
      override val project = myProject
      override val storage = builder
      override val mavenProjectsTree = myProjectsTree
      override lateinit var mavenProjectWithModules: MavenWorkspaceConfigurator.MavenProjectWithModules<ModuleEntity>
    }
    workspaceConfigurators().forEach { configurator ->
      stats.recordConfigurator(configurator, MavenImportCollector.CONFIG_MODULES_DURATION_MS) {
        projectsWithModules.forEach { projectWithModules ->
          try {
            configurator.configureMavenProject(context.apply { mavenProjectWithModules = projectWithModules })
          }
          catch (e: Exception) {
            logErrorIfNotControlFlow("configureMavenProject", e)
          }
        }
      }
    }
  }

  private fun beforeModelApplied(
    projectsWithModules: List<MavenWorkspaceConfigurator.MavenProjectWithModules<ModuleEntity>>,
    builder: MutableEntityStorage,
    contextDataHolder: UserDataHolderBase,
    stats: WorkspaceImportStats,
  ) {
    val context = object : MavenWorkspaceConfigurator.MutableModelContext, UserDataHolderEx by contextDataHolder {
      override val project = myProject
      override val storage = builder
      override val mavenProjectsTree = myProjectsTree
      override val mavenProjectsWithModules = projectsWithModules.asSequence()
      override fun <T : WorkspaceEntity> importedEntities(clazz: Class<T>): Sequence<T> = importedEntities(builder, clazz)
    }
    workspaceConfigurators().forEach { configurator ->
      stats.recordConfigurator(configurator, MavenImportCollector.BEFORE_APPLY_DURATION_MS) {
        try {
          configurator.beforeModelApplied(context)
        }
        catch (e: Exception) {
          logErrorIfNotControlFlow("beforeModelApplied", e)
        }
      }
    }
  }

  private fun afterModelApplied(
    projectsWithModules: List<MavenWorkspaceConfigurator.MavenProjectWithModules<Module>>,
    builder: EntityStorage,
    contextDataHolder: UserDataHolderBase,
    stats: WorkspaceImportStats,
  ) {
    val context = object : MavenWorkspaceConfigurator.AppliedModelContext, UserDataHolderEx by contextDataHolder {
      override val project = myProject
      override val storage = builder
      override val mavenProjectsTree = myProjectsTree
      override val mavenProjectsWithModules = projectsWithModules.asSequence()
      override fun <T : WorkspaceEntity> importedEntities(clazz: Class<T>): Sequence<T> = importedEntities(builder, clazz)
    }
    workspaceConfigurators().forEach { configurator ->
      stats.recordConfigurator(configurator, MavenImportCollector.AFTER_APPLY_DURATION_MS) {
        try {
          configurator.afterModelApplied(context)
        }
        catch (e: Exception) {
          logErrorIfNotControlFlow("afterModelApplied", e)
        }
      }
    }
  }

  protected open fun configLegacyFacets(
    mavenProjectsWithModules: List<MavenProjectWithModulesData<Module>>,
    moduleNameByProject: Map<MavenProject, String>,
    postTasks: List<MavenProjectsProcessorTask>,
    activity: StructuredIdeActivity,
  ) {
    val legacyFacetImporters = mavenProjectsWithModules.flatMap { projectWithModules ->
      projectWithModules.modules.asSequence().mapNotNull { moduleWithType ->
        val importers = MavenImporter.getSuitableImporters(projectWithModules.mavenProject, true)
        MavenProjectImporterUtil.LegacyExtensionImporter.createIfApplicable(projectWithModules.mavenProject,
                                                                            moduleWithType.module,
                                                                            moduleWithType.type,
                                                                            myProjectsTree,
                                                                            projectWithModules.changes,
                                                                            moduleNameByProject,
                                                                            importers)
      }
    }
    MavenProjectImporterUtil.importLegacyExtensions(myProject, myModifiableModelsProvider, legacyFacetImporters, postTasks, activity)
  }

  override fun createdModules(): List<Module> {
    return createdModulesList
  }

  companion object {
    private fun <T : WorkspaceEntity> importedEntities(storage: EntityStorage, clazz: Class<T>) =
      storage.entities(clazz).filter { isMavenEntity(it.entitySource) }

    private fun isMavenEntity(it: EntitySource) =
      (it as? JpsImportedEntitySource)?.externalSystemId == WorkspaceModuleImporter.EXTERNAL_SOURCE_ID
      || it is MavenProjectsTreeEntitySource

    private fun readMavenExternalSystemData(storage: EntityStorage) =
      importedEntities(storage, ExternalSystemModuleOptionsEntity::class.java)
        .mapNotNull { WorkspaceModuleImporter.ExternalSystemData.tryRead(it) }

    private fun hasLegacyImportedModules(storage: EntityStorage) =
      importedEntities(storage, ExternalSystemModuleOptionsEntity::class.java)
        .any { WorkspaceModuleImporter.ExternalSystemData.isFromLegacyImport(it) }

    @JvmStatic
    fun updateTargetFolders(project: Project) {
      val stats = WorkspaceImportStats.startFoldersUpdate(project)
      var numberOfModules = 0
      updateProjectModelFastOrSlow(project, stats, { snapshot ->
        numberOfModules = updateTargetFoldersInSnapshot(project, snapshot, stats)
      })
      stats.finish(numberOfModules)
    }

    private fun updateTargetFoldersInSnapshot(project: Project, builder: MutableEntityStorage, stats: WorkspaceImportStats): Int {
      val folderImportingContext = WorkspaceFolderImporter.FolderImportingContext()

      val mavenManager = MavenProjectsManager.getInstance(project)
      val projectsTree = mavenManager.projectsTree
      val workspaceModel = WorkspaceModel.getInstance(project)
      val importer = WorkspaceFolderImporter(builder,
                                             workspaceModel.getVirtualFileUrlManager(),
                                             mavenManager.importingSettings,
                                             folderImportingContext,
                                             MavenWorkspaceConfigurator.EXTENSION_POINT_NAME.extensionList)

      var numberOfModules = 0
      readMavenExternalSystemData(builder).forEach { data ->
        val pomVirtualFile = VfsUtil.findFile(Path.of(data.mavenProjectFilePath), false) ?: return@forEach
        val mavenProject = projectsTree.findProject(pomVirtualFile) ?: return@forEach

        // remove previously imported content roots
        importedEntities(builder, ContentRootEntity::class.java)
          .filter { it.module == data.moduleEntity }
          .forEach { contentRoot -> builder.removeEntity(contentRoot) }

        // and re-create them with up-to-date data
        importer.createContentRoots(mavenProject, data.mavenModuleType, data.moduleEntity, stats)
        numberOfModules++
      }

      return numberOfModules
    }

    private fun updateProjectModelFastOrSlow(
      project: Project,
      stats: WorkspaceImportStats,
      prepareInBackground: (current: MutableEntityStorage) -> Unit,
      afterApplyInWriteAction: (storage: EntityStorage) -> Unit = {},
    ) {
      val workspaceModel = WorkspaceModel.getInstance(project)
      val prevStorageVersion = (WorkspaceModel.getInstance(project) as WorkspaceModelInternal).entityStorage.version

      var attempts = 0
      var durationInBackground = 0L
      var durationInWriteAction = 0L
      var durationOfWorkspaceUpdate = 0L

      var updated = false
      if (!WORKSPACE_IMPORTER_SKIP_FAST_APPLY_ATTEMPTS_ONCE) {
        while (!updated && attempts < 2) {
          attempts++
          val beforeBG = System.nanoTime()

          val snapshot = (workspaceModel as WorkspaceModelInternal).getBuilderSnapshot()
          val builder = snapshot.builder
          prepareInBackground(builder)
          durationInBackground += System.nanoTime() - beforeBG

          MavenUtil.invokeAndWaitWriteAction(project) {
            val beforeWA = System.nanoTime()
            if (!snapshot.areEntitiesChanged()) {
              updated = true
            }
            else {
              updated = (workspaceModel as WorkspaceModelInternal).replaceProjectModel(snapshot.getStorageReplacement())
              durationOfWorkspaceUpdate = System.nanoTime() - beforeWA
            }
            if (updated) afterApplyInWriteAction(workspaceModel.currentSnapshot)
            durationInWriteAction += System.nanoTime() - beforeWA
          }
          if (updated) break
          MavenLog.LOG.info("Retrying to fast-apply to Workspace Model...")
        }
      }
      WORKSPACE_IMPORTER_SKIP_FAST_APPLY_ATTEMPTS_ONCE = false

      if (!updated) {
        MavenLog.LOG.info("Failed to fast-apply to Workspace Model in $attempts attempts, fallback to slower apply in WriteAction")
        attempts++
        MavenUtil.invokeAndWaitWriteAction(project) {
          val beforeWA = System.nanoTime()
          workspaceModel.updateProjectModel("Maven update project model") { builder -> prepareInBackground(builder) }
          durationOfWorkspaceUpdate = System.nanoTime() - beforeWA
          afterApplyInWriteAction(workspaceModel.currentSnapshot)
          durationInWriteAction += System.nanoTime() - beforeWA
        }
      }

      stats.recordCommitPhaseStats(durationInBackgroundNano = durationInBackground,
                                   durationInWriteActionNano = durationInWriteAction,
                                   durationOfWorkspaceUpdateCallNano = durationOfWorkspaceUpdate,
                                   attempts = attempts)
      val newStorageVersion = (WorkspaceModel.getInstance(project) as WorkspaceModelInternal).entityStorage.version
      LOG.info("Project model updated to version ${newStorageVersion} (attempts: $attempts, previous version: $prevStorageVersion)")
    }

    private val LOG = Logger.getInstance(WorkspaceProjectImporter::class.java)
  }
}

private class AfterImportConfiguratorsTask(
  private val contextData: UserDataHolderBase,
  private val appliedProjectsWithModules: List<MavenProjectWithModulesData<Module>>,
) : MavenProjectsProcessorTask {
  override fun perform(
    project: Project,
    embeddersManager: MavenEmbeddersManager,
    indicator: ProgressIndicator,
  ) {
    val context = object : MavenAfterImportConfigurator.Context, UserDataHolder by contextData {
      override val project = project
      override val mavenProjectsWithModules = appliedProjectsWithModules.asSequence()
      override val progressIndicator = indicator
    }
    for (configurator in AFTER_IMPORT_CONFIGURATOR_EP.extensionList) {
      indicator.checkCanceled()
      try {
        configurator.afterImport(context)
      }
      catch (e: Exception) {
        logErrorIfNotControlFlow("Exception in MavenAfterImportConfigurator.afterImport, skipping it.", e)
      }
    }
  }
}

private fun logErrorIfNotControlFlow(methodName: String, e: Exception) {
  if (e is ControlFlowException) {
    ExceptionUtil.rethrowAllAsUnchecked(e)
  }
  if (e is CancellationException) {
    throw e
  }
  MavenLog.LOG.error("Exception in MavenWorkspaceConfigurator.$methodName, skipping it.", e)
}

internal class ModuleWithTypeData<M>(
  override val module: M,
  override val type: StandardMavenModuleType,
) : MavenWorkspaceConfigurator.ModuleWithType<M>

internal class MavenProjectWithModulesData<M>(
  override val mavenProject: MavenProject,
  override val changes: MavenProjectChanges,
  override val modules: List<ModuleWithTypeData<M>>,
) : MavenWorkspaceConfigurator.MavenProjectWithModules<M>
