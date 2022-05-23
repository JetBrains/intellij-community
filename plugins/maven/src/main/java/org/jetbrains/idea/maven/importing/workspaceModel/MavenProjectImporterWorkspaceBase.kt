// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.idea.maven.importing.MavenModuleImporter
import org.jetbrains.idea.maven.importing.MavenProjectImporterBase
import org.jetbrains.idea.maven.importing.tree.MavenModuleType
import org.jetbrains.idea.maven.project.*

abstract class MavenProjectImporterWorkspaceBase(
  projectsTree: MavenProjectsTree,
  projectsToImportWithChanges: Map<MavenProject, MavenProjectChanges>,
  importingSettings: MavenImportingSettings,
  modelsProvider: IdeModifiableModelsProvider,
  project: Project
) : MavenProjectImporterBase(project, projectsTree, importingSettings, projectsToImportWithChanges, modelsProvider) {
  protected val virtualFileUrlManager = VirtualFileUrlManager.getInstance(project)

  protected fun finalizeImport(modules: List<ModuleImportData>,
                               moduleNameByProject: Map<MavenProject, String>,
                               postTasks: List<MavenProjectsProcessorTask>) {
    val importers = mutableListOf<MavenModuleImporter>()

    for ((module, mavenProject, moduleType) in modules) {
      importers.add(MavenModuleImporter(module,
                                        myProjectsTree,
                                        mavenProject,
                                        myProjectsToImportWithChanges[mavenProject],
                                        moduleNameByProject,
                                        myImportingSettings,
                                        myModelsProvider,
                                        moduleType))

      val mavenId = mavenProject.mavenId
      myModelsProvider.registerModulePublication(module, ProjectId(mavenId.groupId, mavenId.artifactId, mavenId.version))
    }

    configFacets(importers, postTasks)
  }

  protected data class ModuleImportData(val module: Module, val mavenProject: MavenProject, val moduleType: MavenModuleType?)

}