// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree.workspace

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.storage.MutableEntityStorage
import org.jetbrains.idea.maven.importing.tree.MavenProjectImportContextProvider
import org.jetbrains.idea.maven.importing.workspaceModel.MavenProjectImporterWorkspaceBase
import org.jetbrains.idea.maven.importing.workspaceModel.WorkspaceModuleImporterBase
import org.jetbrains.idea.maven.project.*
import java.util.*

class MavenProjectTreeImporterToWorkspaceModel(
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
    val context = MavenProjectImportContextProvider(myProject, myProjectsTree, projectToImport, myImportingSettings,
                                                    mavenProjectToModuleName).context

    val createdModules = mutableListOf<ImportedModuleData>()
    val mavenFolderHolderByMavenId = TreeMap<String, WorkspaceModuleImporterBase.MavenImportFolderHolder>()

    for (importData in context.allModules) {
      val moduleEntity = WorkspaceModuleTreeImporter(
        importData, virtualFileUrlManager, builder, myImportingSettings, mavenFolderHolderByMavenId, myProject
      ).importModule()
      createdModules.add(ImportedModuleData(moduleEntity.persistentId, importData.mavenProject, importData.moduleData.type))
    }
    return createdModules
  }
}