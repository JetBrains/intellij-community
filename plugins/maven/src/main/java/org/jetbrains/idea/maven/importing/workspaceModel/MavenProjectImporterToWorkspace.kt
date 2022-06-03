// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.util.containers.CollectionFactory
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.findModuleByEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.api.ExternalSystemModuleOptionsEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleId
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.idea.maven.importing.MavenModuleImporter
import org.jetbrains.idea.maven.importing.MavenModuleNameMapper
import org.jetbrains.idea.maven.importing.MavenProjectImporterBase
import org.jetbrains.idea.maven.importing.tree.MavenModuleType
import org.jetbrains.idea.maven.importing.tree.MavenProjectImportContextProvider
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.*

class MavenProjectImporterToWorkspace(
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

        finalizeImport(appliedModules, mavenProjectToModuleName, projectToImport, postTasks)

        createdModulesList.addAll(appliedModules.map { it.module })
      }
      finally {
        MavenUtil.invokeAndWaitWriteAction(myProject) { myModelsProvider.dispose() }
      }
    }
    return postTasks

  }

  private fun collectProjectsAndChanges(originalProjectsChanges: Map<MavenProject, MavenProjectChanges>): Pair<Boolean, Map<MavenProject, MavenProjectChanges>> {
    val projectFilesFromPreviousImport = WorkspaceModel.getInstance(myProject).entityStorage.current
      .entities(ExternalSystemModuleOptionsEntity::class.java)
      .filter { it.externalSystem == WorkspaceModuleImporter.EXTERNAL_SOURCE_ID }
      .mapNotNullTo(CollectionFactory.createFilePathSet()) { it.linkedProjectPath }

    val allProjectToImport = myProjectsTree.projects
      .filter { !MavenProjectsManager.getInstance(myProject).isIgnored(it) }
      .associateWith {
        val newProjectToImport = WorkspaceModuleImporter.linkedProjectPath(it) !in projectFilesFromPreviousImport

        if (newProjectToImport) MavenProjectChanges.ALL else originalProjectsChanges.getOrDefault(it, MavenProjectChanges.NONE)
      }

    if (allProjectToImport.values.any { it.hasChanges() }) return true to allProjectToImport

    // check for a situation, when we have a newly ignored project, but no other changes
    val projectFilesToImport = allProjectToImport.keys.mapTo(CollectionFactory.createFilePathSet()) {
      WorkspaceModuleImporter.linkedProjectPath(it)
    }
    return (projectFilesToImport != projectFilesFromPreviousImport) to allProjectToImport
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
    val mavenFolderHolderByMavenId = TreeMap<String, WorkspaceFolderImporter.MavenImportFolderHolder>()

    for (importData in context.allModules) {
      val moduleEntity = WorkspaceModuleImporter(
        myProject, importData, virtualFileUrlManager, builder, myImportingSettings, mavenFolderHolderByMavenId
      ).importModule()
      createdModules.add(ImportedModuleData(moduleEntity.persistentId, importData.mavenProject, importData.moduleData.type))
    }
    return createdModules
  }

  private fun applyModulesToWorkspaceModel(builder: MutableEntityStorage,
                                           createdModules: List<ImportedModuleData>): MutableList<AppliedModuleData> {
    val importModuleData = mutableListOf<AppliedModuleData>()
    MavenUtil.invokeAndWaitWriteAction(myProject) {
      WorkspaceModel.getInstance(myProject).updateProjectModel { current ->
        current.replaceBySource(
          { (it as? JpsImportedEntitySource)?.externalSystemId == WorkspaceModuleImporter.EXTERNAL_SOURCE_ID }, builder)
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

  private fun finalizeImport(modules: List<AppliedModuleData>,
                             moduleNameByProject: Map<MavenProject, String>,
                             projectChanges: Map<MavenProject, MavenProjectChanges>,
                             postTasks: List<MavenProjectsProcessorTask>) {
    val importers = mutableListOf<MavenModuleImporter>()

    for ((module, mavenProject, moduleType) in modules) {
      importers.add(MavenModuleImporter(module,
                                        myProjectsTree,
                                        mavenProject,
                                        projectChanges[mavenProject],
                                        moduleNameByProject,
                                        myImportingSettings,
                                        myModelsProvider,
                                        moduleType))

      val mavenId = mavenProject.mavenId
      myModelsProvider.registerModulePublication(module, ProjectId(mavenId.groupId, mavenId.artifactId, mavenId.version))
    }

    configFacets(importers, postTasks)

    MavenUtil.invokeAndWaitWriteAction(myProject) { removeOutdatedCompilerConfigSettings() }

    scheduleRefreshResolvedArtifacts(postTasks, projectChanges.filterValues { it.hasChanges() }.keys)
  }

  override fun createdModules(): List<Module> {
    return createdModulesList
  }

  protected data class ImportedModuleData(val moduleId: ModuleId, val mavenProject: MavenProject, val moduleType: MavenModuleType?)
  private data class AppliedModuleData(val module: Module, val mavenProject: MavenProject, val moduleType: MavenModuleType?)
}