// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.findModuleByEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleId
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.idea.maven.importing.MavenModuleNameMapper
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
    if (projectsToImportHaveChanges()) {
      try {
        importModules(postTasks)
      }
      finally {
        MavenUtil.invokeAndWaitWriteAction(myProject) { myModelsProvider.dispose() }
      }

      scheduleRefreshResolvedArtifacts(postTasks)
    }
    return postTasks

  }

  private fun importModules(postTasks: ArrayList<MavenProjectsProcessorTask>) {
    val builder = WorkspaceEntityStorageBuilder.create()

    val allProjects = myProjectsTree.projects.toMutableSet()
    allProjects.addAll(myProjectsToImportWithChanges.keys)
    val createdModules = ArrayList<Pair<MavenProject, ModuleId>>()
    val mavenProjectToModuleName = HashMap<MavenProject, String>()
    MavenModuleNameMapper.map(allProjects, emptyMap(), mavenProjectToModuleName, HashMap(), myImportingSettings.dedicatedModuleDir)
    for (mavenProject in allProjects) {
      val moduleEntity = WorkspaceModuleImporter(mavenProject, virtualFileUrlManager, myProjectsTree, builder,
                                                 myImportingSettings, mavenProjectToModuleName, myProject).importModule()
      createdModules.add(mavenProject to moduleEntity.persistentId())
    }

    val moduleImportData = mutableListOf<ModuleImportData>()
    MavenUtil.invokeAndWaitWriteAction(myProject) {
      WorkspaceModel.getInstance(myProject).updateProjectModel { current ->
        current.replaceBySource(
          { (it as? JpsImportedEntitySource)?.externalSystemId == ExternalProjectSystemRegistry.MAVEN_EXTERNAL_SOURCE_ID }, builder)
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