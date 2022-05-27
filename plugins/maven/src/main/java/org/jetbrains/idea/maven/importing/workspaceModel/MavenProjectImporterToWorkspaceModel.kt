// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.util.containers.CollectionFactory
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.findModuleByEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ExternalSystemModuleOptionsEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleId
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.idea.maven.importing.MavenModuleNameMapper
import org.jetbrains.idea.maven.importing.tree.workspace.WorkspaceModuleImporter
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.utils.MavenUtil

class MavenProjectImporterToWorkspaceModel(
  projectsTree: MavenProjectsTree,
  projectsToImportWithChanges: Map<MavenProject, MavenProjectChanges>,
  importingSettings: MavenImportingSettings,
  modelsProvider: IdeModifiableModelsProvider,
  project: Project
) : MavenProjectImporterWorkspaceBase(projectsTree, projectsToImportWithChanges, importingSettings, modelsProvider, project) {
  private val createdModulesList = ArrayList<Module>()

  override fun importProject(): List<MavenProjectsProcessorTask> {

    val postTasks = ArrayList<MavenProjectsProcessorTask>()
    val (hasChanges, projectToImport) = getProjectToImport()
    if (hasChanges) {
      try {
        importModules(projectToImport, postTasks)
      }
      finally {
        MavenUtil.invokeAndWaitWriteAction(myProject) { myModelsProvider.dispose() }
      }

      scheduleRefreshResolvedArtifacts(postTasks)
    }
    return postTasks

  }

  private fun getProjectToImport(): Pair<Boolean, Collection<MavenProject>> {
    val projectFilesFromPreviousImport = WorkspaceModel.getInstance(myProject).entityStorage.current
      .entities(ExternalSystemModuleOptionsEntity::class.java)
      .filter { it.externalSystem == WorkspaceModuleImporter.EXTERNAL_SOURCE_ID }
      .mapNotNullTo(CollectionFactory.createFilePathSet()) { it.linkedProjectPath }

    val allProjectToImport = myProjectsTree.projects
      .filter { !MavenProjectsManager.getInstance(myProject).isIgnored(it) }
      .associateWith {
        val newProjectToImport = WorkspaceModuleImporter.linkedProjectPath(it) !in projectFilesFromPreviousImport

        if (newProjectToImport) MavenProjectChanges.ALL else myProjectsToImportWithChanges.getOrDefault(it, MavenProjectChanges.NONE)
      }

    if (allProjectToImport.values.any { it.hasChanges() }) return true to allProjectToImport.keys

    // check for a situation, when we have a newly ignored project, but no other changes
    val projectFilesToImport = allProjectToImport.keys.mapTo(CollectionFactory.createFilePathSet()) {
      WorkspaceModuleImporter.linkedProjectPath(it)
    }

    return (projectFilesToImport != projectFilesFromPreviousImport) to allProjectToImport.keys
  }

  private fun importModules(projectToImport: Collection<MavenProject>, postTasks: ArrayList<MavenProjectsProcessorTask>) {
    val builder = MutableEntityStorage.create()

    val createdModules = ArrayList<Pair<MavenProject, ModuleId>>()
    val mavenProjectToModuleName = HashMap<MavenProject, String>()
    MavenModuleNameMapper.map(projectToImport, emptyMap(), mavenProjectToModuleName, HashMap(),
                              myImportingSettings.dedicatedModuleDir)
    for (mavenProject in projectToImport) {
      val moduleEntity = WorkspaceModuleImporter(mavenProject, virtualFileUrlManager, myProjectsTree, builder,
                                                 myImportingSettings, mavenProjectToModuleName, myProject).importModule()
      createdModules.add(mavenProject to moduleEntity.persistentId)
    }

    val moduleImportData = mutableListOf<ModuleImportData>()
    MavenUtil.invokeAndWaitWriteAction(myProject) {
      WorkspaceModel.getInstance(myProject).updateProjectModel { current ->
        current.replaceBySource(
          { (it as? JpsImportedEntitySource)?.externalSystemId == WorkspaceModuleImporter.EXTERNAL_SOURCE_ID }, builder)
      }
      val storage = WorkspaceModel.getInstance(myProject).entityStorage.current
      for ((mavenProject, moduleId) in createdModules) {
        val moduleEntity = storage.resolve(moduleId)
        if (moduleEntity == null) continue
        val module = storage.findModuleByEntity(moduleEntity)
        if (module != null) {
          createdModulesList.add(module)
          moduleImportData.add(ModuleImportData(module, mavenProject, null))
        }
      }
    }
    finalizeImport(moduleImportData, mavenProjectToModuleName, postTasks)
  }

  override fun createdModules(): List<Module> = createdModulesList
}