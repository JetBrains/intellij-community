// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
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
import com.intellij.workspaceModel.storage.bridgeEntities.api.ContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ExternalSystemModuleOptionsEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleId
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.idea.maven.importing.MavenImportUtil
import org.jetbrains.idea.maven.importing.MavenLegacyModuleImporter
import org.jetbrains.idea.maven.importing.MavenModuleNameMapper
import org.jetbrains.idea.maven.importing.MavenProjectImporterBase
import org.jetbrains.idea.maven.importing.tree.MavenModuleImportContext
import org.jetbrains.idea.maven.importing.tree.MavenModuleType
import org.jetbrains.idea.maven.importing.tree.MavenProjectImportContextProvider
import org.jetbrains.idea.maven.importing.tree.MavenTreeModuleImportData
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.nio.file.Path

class WorkspaceProjectImporter(
  projectsTree: MavenProjectsTree,
  private val projectsToImportWithChanges: Map<MavenProject, MavenProjectChanges>,
  importingSettings: MavenImportingSettings,
  modelsProvider: IdeModifiableModelsProvider,
  project: Project
) : MavenProjectImporterBase(project, projectsTree, importingSettings, modelsProvider) {
  protected val virtualFileUrlManager = VirtualFileUrlManager.getInstance(project)
  private val createdModulesList = java.util.ArrayList<Module>()

  override fun importProject(): List<MavenProjectsProcessorTask> {
    val postTasks = ArrayList<MavenProjectsProcessorTask>()
    val (hasChanges, projectToImport) = collectProjectsAndChanges(projectsToImportWithChanges)
    if (hasChanges) {
      try {
        val mavenProjectToModuleName = buildModuleNameMap(projectToImport)

        val builder = MutableEntityStorage.create()
        val importedModules = importModules(builder, projectToImport, mavenProjectToModuleName, postTasks)
        val appliedModules = applyModulesToWorkspaceModel(builder, importedModules)

        configLegacyFacets(appliedModules, projectToImport, mavenProjectToModuleName, postTasks)
        finalizeImport(projectToImport, postTasks)

        createdModulesList.addAll(appliedModules.map { it.module })
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
      .filter { !MavenProjectsManager.getInstance(myProject).isIgnored(it) }
      .associateWith {
        val newProjectToImport = it.file.path !in projectFilesFromPreviousImport
        if (newProjectToImport) MavenProjectChanges.ALL else originalProjectsChanges.getOrDefault(it, MavenProjectChanges.NONE)
      }

    if (allProjectToImport.values.any { it.hasChanges() }) return true to allProjectToImport

    // check for a situation, when we have a newly ignored project, but no other changes
    val listOfProjectChanged = allProjectToImport.size != projectFilesFromPreviousImport.size
    return listOfProjectChanged to allProjectToImport
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
                            postTasks: java.util.ArrayList<MavenProjectsProcessorTask>): List<ImportedModuleData> {
    val context = MavenProjectImportContextProvider(myProject, myProjectsTree, projectsToImport, myImportingSettings,
                                                    mavenProjectToModuleName).getContext(projectsToImport.keys)

    val createdModules = mutableListOf<ImportedModuleData>()
    val dependenciesImportingContext = WorkspaceModuleImporter.DependenciesImportingContext()
    val folderImportingContext = WorkspaceFolderImporter.FolderImportingContext()

    for (importData in sortProjectsToImportByPrecedence(context)) {
      val moduleEntity = WorkspaceModuleImporter(myProject,
                                                 importData,
                                                 virtualFileUrlManager,
                                                 builder,
                                                 myImportingSettings,
                                                 dependenciesImportingContext,
                                                 folderImportingContext).importModule()
      createdModules.add(ImportedModuleData(moduleEntity.persistentId, importData.mavenProject, importData.moduleData.type))
    }
    return createdModules
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

  private fun applyModulesToWorkspaceModel(builder: MutableEntityStorage,
                                           createdModules: List<ImportedModuleData>): MutableList<AppliedModuleData> {
    val importModuleData = mutableListOf<AppliedModuleData>()
    MavenUtil.invokeAndWaitWriteAction(myProject) {
      WorkspaceModel.getInstance(myProject).updateProjectModel { current ->
        // remove modules which should be replaced with Maven modules, in order to clean them from pre-existing sources, dependencies etc.
        // It's needed since otherwise 'replaceBySource' will merge pre-existing Module content with imported module content, resulting in
        // unexpected module configuration.
        val importedModuleNames = createdModules.mapTo(mutableSetOf()) { it.moduleId.name }
        current
          .entities(ModuleEntity::class.java)
          .filter { !isMavenEntity(it.entitySource) && it.name in importedModuleNames }
          .forEach { current.removeEntity(it) }

        current.replaceBySource({ isMavenEntity(it) }, builder)
      }
      val storage = WorkspaceModel.getInstance(myProject).entityStorage.current
      for ((moduleId, mavenProject, moduleType) in createdModules) {
        val entity = storage.resolve(moduleId)
        if (entity == null) continue
        val module = storage.findModuleByEntity(entity)
        if (module != null) {
          importModuleData.add(AppliedModuleData(module, mavenProject, moduleType))
        }
      }
    }

    return importModuleData
  }

  private fun configLegacyFacets(modules: List<AppliedModuleData>,
                                 projectChanges: Map<MavenProject, MavenProjectChanges>,
                                 moduleNameByProject: Map<MavenProject, String>,
                                 postTasks: List<MavenProjectsProcessorTask>) {
    val legacyImporters = mutableListOf<MavenLegacyModuleImporter>()
    for ((module, mavenProject, moduleType) in modules) {
      legacyImporters.add(MavenLegacyModuleImporter(module,
                                                    myProjectsTree,
                                                    mavenProject,
                                                    projectChanges[mavenProject],
                                                    moduleNameByProject,
                                                    myImportingSettings,
                                                    myModelsProvider,
                                                    moduleType))
    }
    configFacets(legacyImporters, postTasks)
  }

  private fun finalizeImport(projectChanges: Map<MavenProject, MavenProjectChanges>,
                             postTasks: List<MavenProjectsProcessorTask>) {
    MavenUtil.invokeAndWaitWriteAction(myProject) { removeOutdatedCompilerConfigSettings() }

    val changedProjectsOnly = projectChanges
      .asSequence()
      .filter { (_, changes) -> changes.hasChanges() }
      .map { (mavenProject, _) -> mavenProject }
    scheduleRefreshResolvedArtifacts(postTasks, changedProjectsOnly.asIterable())
  }

  override fun createdModules(): List<Module> {
    return createdModulesList
  }

  private data class ImportedModuleData(val moduleId: ModuleId, val mavenProject: MavenProject, val moduleType: MavenModuleType?)
  private data class AppliedModuleData(val module: Module, val mavenProject: MavenProject, val moduleType: MavenModuleType?)

  companion object {
    private fun isMavenEntity(it: EntitySource) =
      (it as? JpsImportedEntitySource)?.externalSystemId == WorkspaceModuleImporter.EXTERNAL_SOURCE_ID

    private fun readMavenExternalSystemData(storage: EntityStorage) = storage
      .entities(ExternalSystemModuleOptionsEntity::class.java)
      .filter { isMavenEntity(it.entitySource) }
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
        builder
          .entities(ContentRootEntity::class.java)
          .filter { isMavenEntity(it.entitySource) && it.module == data.moduleEntity }
          .forEach { contentRoot -> builder.removeEntity(contentRoot) }

        // and re-create them with up-to-date data
        importer.createContentRoots(mavenProject, data.mavenModuleType, data.moduleEntity)
      }
    }
  }
}