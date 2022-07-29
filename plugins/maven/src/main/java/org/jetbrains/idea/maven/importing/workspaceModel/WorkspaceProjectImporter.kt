// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.LongEventField
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.containers.FileCollectionFactory
import com.intellij.util.indexing.diagnostic.dto.toMillis
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

internal class WorkspaceProjectImporter(
  projectsTree: MavenProjectsTree,
  private val projectsToImportWithChanges: Map<MavenProject, MavenProjectChanges>,
  importingSettings: MavenImportingSettings,
  modelsProvider: IdeModifiableModelsProvider,
  project: Project
) : MavenProjectImporterBase(project, projectsTree, importingSettings, modelsProvider) {
  private val virtualFileUrlManager = VirtualFileUrlManager.getInstance(project)
  private val createdModulesList = java.util.ArrayList<Module>()

  override fun importProject(): List<MavenProjectsProcessorTask> {
    val postTasks = ArrayList<MavenProjectsProcessorTask>()
    val (hasChanges, projectToImport) = collectProjectsAndChanges(projectsToImportWithChanges)
    if (hasChanges) {
      try {
        val mavenProjectToModuleName = buildModuleNameMap(projectToImport)

        val builder = MutableEntityStorage.create()
        val contextData = UserDataHolderBase()
        val configuratorsTimings = ConfiguratorTimings(myProject)

        val projectsWithModuleEntities = importModules(builder, projectToImport, mavenProjectToModuleName, contextData,
                                                       configuratorsTimings)
        val appliedProjectsWithModules = applyModulesToWorkspaceModel(projectsWithModuleEntities, builder, contextData,
                                                                      configuratorsTimings)

        configuratorsTimings.logTiming(projectsWithModuleEntities)

        configLegacyFacets(appliedProjectsWithModules, mavenProjectToModuleName, postTasks)

        val changedProjectsOnly = projectToImport
          .asSequence()
          .filter { (_, changes) -> changes.hasChanges() }
          .map { (mavenProject, _) -> mavenProject }
        scheduleRefreshResolvedArtifacts(postTasks, changedProjectsOnly.asIterable())

        createdModulesList.addAll(appliedProjectsWithModules.flatMap { it.modules.asSequence().map { it.module } })
      }
      finally {
        MavenUtil.invokeAndWaitWriteAction(myProject) { myModelsProvider.dispose() }
      }
    }
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
                            configuratorsTimings: ConfiguratorTimings): List<MavenWorkspaceConfigurator.MavenProjectWithModules<ModuleEntity>> {
    val context = MavenProjectImportContextProvider(myProject, myProjectsTree, myImportingSettings,
                                                    mavenProjectToModuleName).getContext(projectsToImport)

    val dependenciesImportingContext = WorkspaceModuleImporter.DependenciesImportingContext()
    val folderImportingContext = WorkspaceFolderImporter.FolderImportingContext()

    class PartialModulesData(val changes: MavenProjectChanges,
                             val modules: MutableList<MavenWorkspaceConfigurator.ModuleWithType<ModuleEntity>>)

    val projectToModulesData = mutableMapOf<MavenProject, PartialModulesData>()
    for (importData in sortProjectsToImportByPrecedence(context)) {
      val moduleEntity = WorkspaceModuleImporter(myProject,
                                                 importData,
                                                 virtualFileUrlManager,
                                                 builder,
                                                 myImportingSettings,
                                                 dependenciesImportingContext,
                                                 folderImportingContext,
                                                 configuratorsTimings).importModule()

      val partialData = projectToModulesData.computeIfAbsent(importData.mavenProject, Function {
        PartialModulesData(importData.changes ?: MavenProjectChanges.NONE, mutableListOf())
      })
      partialData.modules.add(ModuleWithTypeData(moduleEntity, importData.moduleData.type))
    }

    val result = projectToModulesData.map { (mavenProject, partialData) ->
      MavenProjectWithModulesData(mavenProject, partialData.changes, partialData.modules)
    }

    configureModules(result, builder, contextData, configuratorsTimings)
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

  private fun applyModulesToWorkspaceModel(mavenProjectsWithModules: List<MavenWorkspaceConfigurator.MavenProjectWithModules<ModuleEntity>>,
                                           builder: MutableEntityStorage,
                                           contextData: UserDataHolderBase,
                                           configuratorsTimings: ConfiguratorTimings)
    : List<MavenWorkspaceConfigurator.MavenProjectWithModules<Module>> {

    beforeModelApplied(mavenProjectsWithModules, builder, contextData, configuratorsTimings)

    val result = mutableListOf<MavenWorkspaceConfigurator.MavenProjectWithModules<Module>>()
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

      afterModelApplied(result, storage, contextData, configuratorsTimings)
    }
    return result
  }

  private fun configureModules(projectsWithModules: List<MavenWorkspaceConfigurator.MavenProjectWithModules<ModuleEntity>>,
                               builder: MutableEntityStorage,
                               contextDataHolder: UserDataHolderBase,
                               configuratorsTimings: ConfiguratorTimings) {
    val context = object : MavenWorkspaceConfigurator.MutableMavenProjectContext, UserDataHolder by contextDataHolder {
      override val project = myProject
      override val storage = builder
      override val mavenProjectsTree = myProjectsTree
      override lateinit var mavenProjectWithModules: MavenWorkspaceConfigurator.MavenProjectWithModules<ModuleEntity>
    }
    WORKSPACE_CONFIGURATOR_EP.extensions.forEach { configurator ->
      configuratorsTimings.record(configurator, MavenImportCollector.CONFIG_MODULES_DURATION_MS) {
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
                                 configuratorsTimings: ConfiguratorTimings) {
    val context = object : MavenWorkspaceConfigurator.MutableModelContext, UserDataHolder by contextDataHolder {
      override val project = myProject
      override val storage = builder
      override val mavenProjectsTree = myProjectsTree
      override val mavenProjectsWithModules = projectsWithModules.asSequence()
      override fun <T : WorkspaceEntity> importedEntities(clazz: Class<T>): Sequence<T> = Companion.importedEntities(builder, clazz)
    }
    WORKSPACE_CONFIGURATOR_EP.extensions.forEach { configurator ->
      configuratorsTimings.record(configurator, MavenImportCollector.BEFORE_APPLY_DURATION_MS) {
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
                                configuratorsTimings: ConfiguratorTimings) {
    val context = object : MavenWorkspaceConfigurator.AppliedModelContext, UserDataHolder by contextDataHolder {
      override val project = myProject
      override val storage = builder
      override val mavenProjectsTree = myProjectsTree
      override val mavenProjectsWithModules = projectsWithModules.asSequence()
      override fun <T : WorkspaceEntity> importedEntities(clazz: Class<T>): Sequence<T> = Companion.importedEntities(builder, clazz)
    }
    WORKSPACE_CONFIGURATOR_EP.extensions.forEach { configurator ->
      configuratorsTimings.record(configurator, MavenImportCollector.AFTER_APPLY_DURATION_MS) {
        try {
          configurator.afterModelApplied(context)
        }
        catch (e: Exception) {
          MavenLog.LOG.error(e)
        }
      }
    }
  }

  private fun configLegacyFacets(mavenProjectsWithModules: List<MavenWorkspaceConfigurator.MavenProjectWithModules<Module>>,
                                 moduleNameByProject: Map<MavenProject, String>,
                                 postTasks: List<MavenProjectsProcessorTask>) {
    val legacyImporters = mavenProjectsWithModules.flatMap { projectWithModules ->
      projectWithModules.modules.asSequence().map { moduleWithType ->
        MavenLegacyModuleImporter(moduleWithType.module,
                                  myProjectsTree,
                                  projectWithModules.mavenProject,
                                  projectWithModules.changes,
                                  moduleNameByProject,
                                  myImportingSettings,
                                  myModelsProvider,
                                  moduleWithType.type)
      }
    }
    configFacets(legacyImporters, postTasks, /* isWorkspaceImport = */ true)
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

      readMavenExternalSystemData(builder).forEach { data ->
        val pomVirtualFile = VfsUtil.findFile(Path.of(data.mavenProjectFilePath), false) ?: return@forEach
        val mavenProject = projectsTree.findProject(pomVirtualFile) ?: return@forEach

        // remove previously imported content roots
        importedEntities(builder, ContentRootEntity::class.java)
          .filter { it.module == data.moduleEntity }
          .forEach { contentRoot -> builder.removeEntity(contentRoot) }

        // and re-create them with up-to-date data
        importer.createContentRoots(mavenProject, data.mavenModuleType, data.moduleEntity, ConfiguratorTimings(project))
      }
    }
  }
}

internal class ConfiguratorTimings(private val project: Project) {
  private val data = mutableMapOf<Class<MavenWorkspaceConfigurator>, MutableMap<LongEventField, Long>>()

  fun <T> record(configurator: MavenWorkspaceConfigurator,
                 key: LongEventField,
                 block: () -> T): T {
    val start = System.nanoTime()
    return try {
      block()
    }
    finally {
      val durationNano = System.nanoTime() - start
      val mapping = data.computeIfAbsent(configurator.javaClass) { mutableMapOf() }
      mapping.compute(key) { _, value -> (value ?: 0) + durationNano }
    }
  }

  internal fun logTiming(projectsWithModules: List<MavenWorkspaceConfigurator.MavenProjectWithModules<ModuleEntity>>) {
    for ((clazz, timings) in data) {
      val logPairs = mutableListOf<EventPair<*>>()
      var totalNano = 0L
      timings.forEach { (key, nano) ->
        logPairs.add(key.with(nano.toMillis()))
        totalNano += nano
      }
      logPairs.add(MavenImportCollector.TOTAL_DURATION_MS.with(totalNano.toMillis()))
      logPairs.add(MavenImportCollector.CONFIGURATOR_CLASS.with(clazz))
      logPairs.add(MavenImportCollector.NUMBER_OF_MODULES.with(projectsWithModules.sumOf { it.modules.size }))

      MavenImportCollector.CONFIGURATOR_RUN.log(project, logPairs)
    }
  }

}

private class ModuleWithTypeData<M>(
  override val module: M,
  override val type: MavenModuleType) : MavenWorkspaceConfigurator.ModuleWithType<M>

private class MavenProjectWithModulesData<M>(
  override val mavenProject: MavenProject,
  override val changes: MavenProjectChanges,
  override val modules: List<MavenWorkspaceConfigurator.ModuleWithType<M>>) : MavenWorkspaceConfigurator.MavenProjectWithModules<M>
