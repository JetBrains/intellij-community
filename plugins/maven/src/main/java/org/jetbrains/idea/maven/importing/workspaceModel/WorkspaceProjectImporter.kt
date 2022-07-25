// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.containers.FileCollectionFactory
import com.intellij.util.indexing.diagnostic.TimeNano
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
import kotlin.reflect.KMutableProperty1
import kotlin.system.measureNanoTime

class WorkspaceProjectImporter(
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
        val configuratorsTimings = mutableMapOf<Class<MavenConfigurator>, ConfiguratorTiming>()

        val projectsWithModuleEntities = importModules(builder, projectToImport, mavenProjectToModuleName, contextData,
                                                       configuratorsTimings)
        val appliedProjectsWithModules = applyModulesToWorkspaceModel(projectsWithModuleEntities, builder, contextData,
                                                                      configuratorsTimings)

        logConfiguratorTiming(configuratorsTimings, projectsWithModuleEntities)

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
                            configuratorsTimings: MutableMap<Class<MavenConfigurator>, ConfiguratorTiming>): List<MavenConfigurator.MavenProjectWithModules<ModuleEntity>> {
    val context = MavenProjectImportContextProvider(myProject, myProjectsTree, myImportingSettings,
                                                    mavenProjectToModuleName).getContext(projectsToImport)

    val dependenciesImportingContext = WorkspaceModuleImporter.DependenciesImportingContext()
    val folderImportingContext = WorkspaceFolderImporter.FolderImportingContext()

    class PartialModulesData(val changes: MavenProjectChanges,
                             val modules: MutableList<MavenConfigurator.ModuleWithType<ModuleEntity>>)

    val projectToModulesData = mutableMapOf<MavenProject, PartialModulesData>()
    for (importData in sortProjectsToImportByPrecedence(context)) {
      val moduleEntity = WorkspaceModuleImporter(myProject,
                                                 importData,
                                                 virtualFileUrlManager,
                                                 builder,
                                                 myImportingSettings,
                                                 dependenciesImportingContext,
                                                 folderImportingContext).importModule()

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

  private fun applyModulesToWorkspaceModel(mavenProjectsWithModules: List<MavenConfigurator.MavenProjectWithModules<ModuleEntity>>,
                                           builder: MutableEntityStorage,
                                           contextData: UserDataHolderBase,
                                           configuratorsTimings: MutableMap<Class<MavenConfigurator>, ConfiguratorTiming>)
    : List<MavenConfigurator.MavenProjectWithModules<Module>> {

    beforeModelApplied(mavenProjectsWithModules, builder, contextData, configuratorsTimings)

    val result = mutableListOf<MavenConfigurator.MavenProjectWithModules<Module>>()
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

  private fun configureModules(projectsWithModules: List<MavenConfigurator.MavenProjectWithModules<ModuleEntity>>,
                               builder: MutableEntityStorage,
                               contextDataHolder: UserDataHolderBase,
                               configuratorsTimings: MutableMap<Class<MavenConfigurator>, ConfiguratorTiming>) {
    val context = object : MavenConfigurator.MutableMavenProjectContext, UserDataHolder by contextDataHolder {
      override val project = myProject
      override val storage = builder
      override val mavenProjectsTree = myProjectsTree
      override lateinit var mavenProjectWithModules: MavenConfigurator.MavenProjectWithModules<ModuleEntity>
    }
    MavenConfigurator.EXTENSION_POINT_NAME.extensions.forEach { configurator ->
      recordConfiguratorTiming(configurator, configuratorsTimings, ConfiguratorTiming::configModulesNano) {
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

  private fun beforeModelApplied(projectsWithModules: List<MavenConfigurator.MavenProjectWithModules<ModuleEntity>>,
                                 builder: MutableEntityStorage,
                                 contextDataHolder: UserDataHolderBase,
                                 configuratorsTimings: MutableMap<Class<MavenConfigurator>, ConfiguratorTiming>) {
    val context = object : MavenConfigurator.MutableModelContext, UserDataHolder by contextDataHolder {
      override val project = myProject
      override val storage = builder
      override val mavenProjectsTree = myProjectsTree
      override val mavenProjectsWithModules = projectsWithModules.asSequence()
      override fun <T : WorkspaceEntity> importedEntities(clazz: Class<T>): Sequence<T> = Companion.importedEntities(builder, clazz)
    }
    MavenConfigurator.EXTENSION_POINT_NAME.extensions.forEach { configurator ->
      recordConfiguratorTiming(configurator, configuratorsTimings, ConfiguratorTiming::beforeApplyNano) {
        try {
          configurator.beforeModelApplied(context)
        }
        catch (e: Exception) {
          MavenLog.LOG.error(e)
        }
      }
    }
  }

  private fun afterModelApplied(projectsWithModules: List<MavenConfigurator.MavenProjectWithModules<Module>>,
                                builder: EntityStorage,
                                contextDataHolder: UserDataHolderBase,
                                configuratorsTimings: MutableMap<Class<MavenConfigurator>, ConfiguratorTiming>) {
    val context = object : MavenConfigurator.AppliedModelContext, UserDataHolder by contextDataHolder {
      override val project = myProject
      override val storage = builder
      override val mavenProjectsTree = myProjectsTree
      override val mavenProjectsWithModules = projectsWithModules.asSequence()
      override fun <T : WorkspaceEntity> importedEntities(clazz: Class<T>): Sequence<T> = Companion.importedEntities(builder, clazz)
    }
    MavenConfigurator.EXTENSION_POINT_NAME.extensions.forEach { configurator ->
      recordConfiguratorTiming(configurator, configuratorsTimings, ConfiguratorTiming::afterApplyNano) {
        try {
          configurator.afterModelApplied(context)
        }
        catch (e: Exception) {
          MavenLog.LOG.error(e)
        }
      }
    }
  }

  private fun recordConfiguratorTiming(configurator: MavenConfigurator,
                                       timings: MutableMap<Class<MavenConfigurator>, ConfiguratorTiming>,
                                       accessor: KMutableProperty1<ConfiguratorTiming, TimeNano>,
                                       block: () -> Unit) {
    val durationNano = measureNanoTime(block)
    val timing = timings.computeIfAbsent(configurator.javaClass) { ConfiguratorTiming() }
    accessor.set(timing, accessor.get(timing) + durationNano)
  }

  private fun logConfiguratorTiming(timings: MutableMap<Class<MavenConfigurator>, ConfiguratorTiming>,
                                    projectsWithModules: List<MavenConfigurator.MavenProjectWithModules<ModuleEntity>>) {
    for ((clazz, timing) in timings) {
      MavenImportCollector.CONFIGURATOR_RUN.log(myProject,
                                                MavenImportCollector.CONFIGURATOR_CLASS.with(clazz),
                                                MavenImportCollector.NUMBER_OF_MODULES.with(projectsWithModules.sumOf { it.modules.size }),
                                                MavenImportCollector.TOTAL_DURATION_MS.with(timing.totalNano.toMillis()),
                                                MavenImportCollector.CONFIG_MODULES_DURATION_MS.with(timing.configModulesNano.toMillis()),
                                                MavenImportCollector.BEFORE_APPLY_DURATION_MS.with(timing.beforeApplyNano.toMillis()),
                                                MavenImportCollector.AFTER_APPLY_DURATION_MS.with(timing.afterApplyNano.toMillis()))
    }
  }

  private data class ConfiguratorTiming(var configModulesNano: TimeNano = 0,
                                        var beforeApplyNano: TimeNano = 0,
                                        var afterApplyNano: TimeNano = 0) {
    val totalNano get() = configModulesNano + beforeApplyNano + afterApplyNano
  }

  private fun configLegacyFacets(mavenProjectsWithModules: List<MavenConfigurator.MavenProjectWithModules<Module>>,
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
        importer.createContentRoots(mavenProject, data.mavenModuleType, data.moduleEntity)
      }
    }
  }
}

private class ModuleWithTypeData<M>(
  override val module: M,
  override val type: MavenModuleType) : MavenConfigurator.ModuleWithType<M>

private class MavenProjectWithModulesData<M>(
  override val mavenProject: MavenProject,
  override val changes: MavenProjectChanges,
  override val modules: List<MavenConfigurator.ModuleWithType<M>>) : MavenConfigurator.MavenProjectWithModules<M>
