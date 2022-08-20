// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.containers.FileCollectionFactory
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.findModuleByEntity
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ExternalSystemModuleOptionsEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.idea.maven.importing.*
import org.jetbrains.idea.maven.importing.tree.MavenModuleImportContext
import org.jetbrains.idea.maven.importing.tree.MavenProjectImportContextProvider
import org.jetbrains.idea.maven.importing.tree.MavenTreeModuleImportData
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.statistics.MavenImportCollector
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.nio.file.Path
import java.util.function.Function

internal val WORKSPACE_CONFIGURATOR_EP: ExtensionPointName<MavenWorkspaceConfigurator> = ExtensionPointName.create(
  "org.jetbrains.idea.maven.importing.workspaceConfigurator")
internal val AFTER_IMPORT_CONFIGURATOR_EP: ExtensionPointName<MavenAfterImportConfigurator> = ExtensionPointName.create(
  "org.jetbrains.idea.maven.importing.afterImportConfigurator")

internal class WorkspaceProjectImporter(
  private val myProjectsTree: MavenProjectsTree,
  private val projectsToImportWithChanges: Map<MavenProject, MavenProjectChanges>,
  private val myImportingSettings: MavenImportingSettings,
  private val myModifiableModelsProvider: IdeModifiableModelsProvider,
  private val myProject: Project
) : MavenProjectImporter {
  private val virtualFileUrlManager = VirtualFileUrlManager.getInstance(myProject)
  private val createdModulesList = java.util.ArrayList<Module>()

  override fun importProject(): List<MavenProjectsProcessorTask> {
    val (hasChanges, projectToImport) = collectProjectsAndChanges(projectsToImportWithChanges)
    if (!hasChanges) return emptyList()

    val postTasks = ArrayList<MavenProjectsProcessorTask>()
    val stats = WorkspaceImportStats.start(myProject)

    val mavenProjectToModuleName = buildModuleNameMap(projectToImport)

    val builder = MutableEntityStorage.create()
    val contextData = UserDataHolderBase()

    val projectsWithModuleEntities = stats.recordPhase(MavenImportCollector.WORKSPACE_POPULATE_PHASE) {
      importModules(builder, projectToImport, mavenProjectToModuleName, contextData, stats)
    }
    val appliedProjectsWithModules = stats.recordPhase(MavenImportCollector.WORKSPACE_COMMIT_PHASE) {
      applyModulesToWorkspaceModel(projectsWithModuleEntities, builder, contextData, stats)
    }

    stats.recordPhase(MavenImportCollector.WORKSPACE_LEGACY_IMPORTERS_PHASE) {
      configLegacyFacets(appliedProjectsWithModules, mavenProjectToModuleName, postTasks)
    }

    val changedProjectsOnly = projectToImport
      .asSequence()
      .filter { (_, changes) -> changes.hasChanges() }
      .map { (mavenProject, _) -> mavenProject }
    MavenProjectImporterBase.scheduleRefreshResolvedArtifacts(postTasks, changedProjectsOnly.asIterable())

    createdModulesList.addAll(appliedProjectsWithModules.flatMap { it.modules.asSequence().map { it.module } })

    stats.finish(numberOfModules = projectsWithModuleEntities.sumOf { it.modules.size })

    postTasks.add(MavenProjectsProcessorTask { _, _, _, indicator ->
      val context = object : MavenAfterImportConfigurator.Context, UserDataHolder by contextData {
        override val project = myProject
        override val mavenProjectsWithModules = appliedProjectsWithModules.asSequence()
      }
      for (configurator in AFTER_IMPORT_CONFIGURATOR_EP.extensionList) {
        indicator.checkCanceled()
        configurator.afterImport(context)
      }
    })

    return postTasks

  }

  private fun collectProjectsAndChanges(originalProjectsChanges: Map<MavenProject, MavenProjectChanges>): Pair<Boolean, Map<MavenProject, MavenProjectChanges>> {
    val projectFilesFromPreviousImport = readMavenExternalSystemData(WorkspaceModel.getInstance(myProject).entityStorage.current)
      .mapTo(FileCollectionFactory.createCanonicalFilePathSet()) { it.mavenProjectFilePath }

    val allProjectToImport = myProjectsTree.projects
      .filter { !myProjectsTree.isIgnored(it) }
      .associateWith {
        val newProjectToImport = it.file.path !in projectFilesFromPreviousImport
        if (newProjectToImport) MavenProjectChanges.ALL else originalProjectsChanges.getOrDefault(it, MavenProjectChanges.NONE)
      }

    var hasChanges = allProjectToImport.values.any { it.hasChanges() }
    if (!hasChanges) {
      // check for a situation, when we have a newly ignored project, but no other changes
      val listOfProjectChanged = allProjectToImport.size != projectFilesFromPreviousImport.size
      hasChanges = listOfProjectChanged
    }
    return hasChanges to allProjectToImport
  }

  private fun buildModuleNameMap(projectToImport: Map<MavenProject, MavenProjectChanges>): HashMap<MavenProject, String> {
    val mavenProjectToModuleName = HashMap<MavenProject, String>()
    MavenModuleNameMapper.map(projectToImport.keys, emptyMap(), mavenProjectToModuleName, HashMap(),
                              myImportingSettings.dedicatedModuleDir)
    return mavenProjectToModuleName
  }

  private fun importModules(builder: MutableEntityStorage,
                            projectsToImport: Map<MavenProject, MavenProjectChanges>,
                            mavenProjectToModuleName: java.util.HashMap<MavenProject, String>,
                            contextData: UserDataHolderBase,
                            stats: WorkspaceImportStats): List<MavenProjectWithModulesData<ModuleEntity>> {
    val context = MavenProjectImportContextProvider(myProject, myProjectsTree, myImportingSettings,
                                                    mavenProjectToModuleName).getContext(projectsToImport)

    val dependenciesImportingContext = WorkspaceModuleImporter.DependenciesImportingContext()
    val folderImportingContext = WorkspaceFolderImporter.FolderImportingContext()

    class PartialModulesData(val changes: MavenProjectChanges,
                             val modules: MutableList<ModuleWithTypeData<ModuleEntity>>)

    val projectToModulesData = mutableMapOf<MavenProject, PartialModulesData>()
    for (importData in sortProjectsToImportByPrecedence(context)) {
      val moduleEntity = WorkspaceModuleImporter(myProject,
                                                 importData,
                                                 virtualFileUrlManager,
                                                 builder,
                                                 myImportingSettings,
                                                 dependenciesImportingContext,
                                                 folderImportingContext,
                                                 stats).importModule()

      val partialData = projectToModulesData.computeIfAbsent(importData.mavenProject, Function {
        PartialModulesData(importData.changes, mutableListOf())
      })
      partialData.modules.add(ModuleWithTypeData(moduleEntity, importData.moduleData.type))
    }

    val result = projectToModulesData.map { (mavenProject, partialData) ->
      MavenProjectWithModulesData(mavenProject, partialData.changes, partialData.modules)
    }

    configureModules(result, builder, contextData, stats)
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

  private fun applyModulesToWorkspaceModel(mavenProjectsWithModules: List<MavenProjectWithModulesData<ModuleEntity>>,
                                           builder: MutableEntityStorage,
                                           contextData: UserDataHolderBase,
                                           stats: WorkspaceImportStats)
    : List<MavenProjectWithModulesData<Module>> {

    beforeModelApplied(mavenProjectsWithModules, builder, contextData, stats)

    val result = mutableListOf<MavenProjectWithModulesData<Module>>()
    MavenUtil.invokeAndWaitWriteAction(myProject) {
      WorkspaceModel.getInstance(myProject).updateProjectModel { storage ->
        // remove modules which should be replaced with Maven modules, in order to clean them from pre-existing sources, dependencies etc.
        // It's needed since otherwise 'replaceBySource' will merge pre-existing Module content with imported module content, resulting in
        // unexpected module configuration.
        val importedModuleNames = mavenProjectsWithModules
          .flatMapTo(mutableSetOf()) { it.modules.asSequence().map { it.module.name } }

        storage
          .entities(ModuleEntity::class.java)
          .filter { !isMavenEntity(it.entitySource) && it.name in importedModuleNames }
          .forEach { storage.removeEntity(it) }

        storage.replaceBySource({ isMavenEntity(it) }, builder)
      }
      val storage = WorkspaceModel.getInstance(myProject).entityStorage.current

      // map ModuleEntities to the created Modules
      for (each in mavenProjectsWithModules) {
        val appliedModules = each.modules.mapNotNull {
          val originalEntity = it.module
          val appliedEntity = storage.resolve(originalEntity.persistentId) ?: return@mapNotNull null
          val module = storage.findModuleByEntity(appliedEntity) ?: return@mapNotNull null
          ModuleWithTypeData<Module>(module, it.type)
        }

        if (appliedModules.isNotEmpty()) {
          result.add(MavenProjectWithModulesData(each.mavenProject, each.changes, appliedModules))
        }
      }

      afterModelApplied(result, storage, contextData, stats)
    }
    return result
  }

  private fun configureModules(projectsWithModules: List<MavenWorkspaceConfigurator.MavenProjectWithModules<ModuleEntity>>,
                               builder: MutableEntityStorage,
                               contextDataHolder: UserDataHolderBase,
                               stats: WorkspaceImportStats) {
    val context = object : MavenWorkspaceConfigurator.MutableMavenProjectContext, UserDataHolder by contextDataHolder {
      override val project = myProject
      override val storage = builder
      override val mavenProjectsTree = myProjectsTree
      override lateinit var mavenProjectWithModules: MavenWorkspaceConfigurator.MavenProjectWithModules<ModuleEntity>
    }
    WORKSPACE_CONFIGURATOR_EP.extensions.forEach { configurator ->
      stats.recordConfigurator(configurator, MavenImportCollector.CONFIG_MODULES_DURATION_MS) {
        projectsWithModules.forEach { projectWithModules ->
          try {
            configurator.configureMavenProject(context.apply { mavenProjectWithModules = projectWithModules })
          }
          catch (e: Exception) {
            MavenLog.LOG.error(e)
          }
        }
      }
    }
  }

  private fun beforeModelApplied(projectsWithModules: List<MavenWorkspaceConfigurator.MavenProjectWithModules<ModuleEntity>>,
                                 builder: MutableEntityStorage,
                                 contextDataHolder: UserDataHolderBase,
                                 stats: WorkspaceImportStats) {
    val context = object : MavenWorkspaceConfigurator.MutableModelContext, UserDataHolder by contextDataHolder {
      override val project = myProject
      override val storage = builder
      override val mavenProjectsTree = myProjectsTree
      override val mavenProjectsWithModules = projectsWithModules.asSequence()
      override fun <T : WorkspaceEntity> importedEntities(clazz: Class<T>): Sequence<T> = Companion.importedEntities(builder, clazz)
    }
    WORKSPACE_CONFIGURATOR_EP.extensions.forEach { configurator ->
      stats.recordConfigurator(configurator, MavenImportCollector.BEFORE_APPLY_DURATION_MS) {
        try {
          configurator.beforeModelApplied(context)
        }
        catch (e: Exception) {
          MavenLog.LOG.error(e)
        }
      }
    }
  }

  private fun afterModelApplied(projectsWithModules: List<MavenWorkspaceConfigurator.MavenProjectWithModules<Module>>,
                                builder: EntityStorage,
                                contextDataHolder: UserDataHolderBase,
                                stats: WorkspaceImportStats) {
    val context = object : MavenWorkspaceConfigurator.AppliedModelContext, UserDataHolder by contextDataHolder {
      override val project = myProject
      override val storage = builder
      override val mavenProjectsTree = myProjectsTree
      override val mavenProjectsWithModules = projectsWithModules.asSequence()
      override fun <T : WorkspaceEntity> importedEntities(clazz: Class<T>): Sequence<T> = Companion.importedEntities(builder, clazz)
    }
    WORKSPACE_CONFIGURATOR_EP.extensions.forEach { configurator ->
      stats.recordConfigurator(configurator, MavenImportCollector.AFTER_APPLY_DURATION_MS) {
        try {
          configurator.afterModelApplied(context)
        }
        catch (e: Exception) {
          MavenLog.LOG.error(e)
        }
      }
    }
  }

  private fun configLegacyFacets(mavenProjectsWithModules: List<MavenProjectWithModulesData<Module>>,
                                 moduleNameByProject: Map<MavenProject, String>,
                                 postTasks: List<MavenProjectsProcessorTask>) {
    val legacyFacetImporters = mavenProjectsWithModules.flatMap { projectWithModules ->
      projectWithModules.modules.asSequence().mapNotNull { moduleWithType ->
        MavenLegacyModuleImporter.ExtensionImporter.createIfApplicable(projectWithModules.mavenProject,
                                                                       moduleWithType.module,
                                                                       moduleWithType.type,
                                                                       myProjectsTree,
                                                                       projectWithModules.changes,
                                                                       moduleNameByProject,
                                                                       /* isWorkspaceImport = */true)
      }
    }
    MavenProjectImporterBase.importExtensions(myProject, myModifiableModelsProvider, legacyFacetImporters, postTasks)
  }

  override fun createdModules(): List<Module> {
    return createdModulesList
  }

  companion object {
    private fun <T : WorkspaceEntity> importedEntities(storage: EntityStorage, clazz: Class<T>) =
      storage.entities(clazz).filter { isMavenEntity(it.entitySource) }

    private fun isMavenEntity(it: EntitySource) =
      (it as? JpsImportedEntitySource)?.externalSystemId == WorkspaceModuleImporter.EXTERNAL_SOURCE_ID

    private fun readMavenExternalSystemData(storage: EntityStorage) =
      importedEntities(storage, ExternalSystemModuleOptionsEntity::class.java)
        .mapNotNull { WorkspaceModuleImporter.ExternalSystemData.tryRead(it) }

    @JvmStatic
    fun tryUpdateTargetFolders(project: Project) {
      val snapshot = WorkspaceModel.getInstance(project).getBuilderSnapshot()
      val builder = snapshot.builder

      repeat(2) {
        updateTargetFoldersInSnapshot(project, builder)

        var replaced = false
        MavenUtil.invokeAndWaitWriteAction(project) {
          replaced = WorkspaceModel.getInstance(project).replaceProjectModel(snapshot.getStorageReplacement())
        }
        if (replaced) return
      }
      MavenLog.LOG.info("Cannot update project folders: workspace is already modified")
    }

    private fun updateTargetFoldersInSnapshot(project: Project, builder: MutableEntityStorage) {
      val folderImportingContext = WorkspaceFolderImporter.FolderImportingContext()

      val mavenManager = MavenProjectsManager.getInstance(project)
      val projectsTree = mavenManager.projectsTree
      val importer = WorkspaceFolderImporter(builder,
                                             VirtualFileUrlManager.getInstance(project),
                                             mavenManager.importingSettings,
                                             folderImportingContext)

      val stats = WorkspaceImportStats.startFoldersUpdate(project)
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
      stats.finish(numberOfModules)
    }
  }
}

private class ModuleWithTypeData<M>(
  override val module: M,
  override val type: StandardMavenModuleType) : MavenWorkspaceConfigurator.ModuleWithType<M>

private class MavenProjectWithModulesData<M>(
  override val mavenProject: MavenProject,
  override val changes: MavenProjectChanges,
  override val modules: List<ModuleWithTypeData<M>>) : MavenWorkspaceConfigurator.MavenProjectWithModules<M>
