// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.storage.MutableEntityStorage
import org.jetbrains.idea.maven.project.*

class MavenProjectImporterToWorkspaceModel(
  projectsTree: MavenProjectsTree,
  projectsToImportWithChanges: Map<MavenProject, MavenProjectChanges>,
  importingSettings: MavenImportingSettings,
  modelsProvider: IdeModifiableModelsProvider,
  project: Project
) : MavenProjectImporterWorkspaceBase(projectsTree, projectsToImportWithChanges, importingSettings, modelsProvider, project) {

  override fun importModules(builder: MutableEntityStorage,
                             projectToImport: Map<MavenProject, MavenProjectChanges>,
                             mavenProjectToModuleName: HashMap<MavenProject, String>,
                             postTasks: ArrayList<MavenProjectsProcessorTask>): List<ImportedModuleData> {
    val createdModules = mutableListOf<ImportedModuleData>()
    for (mavenProject in projectToImport.keys) {
      val moduleEntity = WorkspaceModuleImporter(mavenProject, virtualFileUrlManager, myProjectsTree, builder,
                                                 myImportingSettings, mavenProjectToModuleName, myProject).importModule()
      createdModules.add(ImportedModuleData(moduleEntity.persistentId, mavenProject, null))
    }
    return createdModules
  }
}