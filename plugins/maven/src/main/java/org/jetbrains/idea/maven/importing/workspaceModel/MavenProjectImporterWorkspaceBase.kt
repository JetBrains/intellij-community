// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.util.containers.CollectionFactory
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.storage.bridgeEntities.ExternalSystemModuleOptionsEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.idea.maven.importing.MavenModuleImporter
import org.jetbrains.idea.maven.importing.MavenProjectImporterBase
import org.jetbrains.idea.maven.importing.tree.MavenModuleType
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.utils.MavenUtil

abstract class MavenProjectImporterWorkspaceBase(
  projectsTree: MavenProjectsTree,
  importingSettings: MavenImportingSettings,
  modelsProvider: IdeModifiableModelsProvider,
  project: Project
) : MavenProjectImporterBase(project, projectsTree, importingSettings, modelsProvider) {
  protected val virtualFileUrlManager = VirtualFileUrlManager.getInstance(project)

  protected fun collectProjectsAndChanges(originalProjectsChanges: Map<MavenProject, MavenProjectChanges>): Pair<Boolean, Map<MavenProject, MavenProjectChanges>> {
    val projectFilesFromPreviousImport = WorkspaceModel.getInstance(myProject).entityStorage.current
      .entities(ExternalSystemModuleOptionsEntity::class.java)
      .filter { it.externalSystem == WorkspaceModuleImporterBase.EXTERNAL_SOURCE_ID }
      .mapNotNullTo(CollectionFactory.createFilePathSet()) { it.linkedProjectPath }

    val allProjectToImport = myProjectsTree.projects
      .filter { !MavenProjectsManager.getInstance(myProject).isIgnored(it) }
      .associateWith {
        val newProjectToImport = WorkspaceModuleImporterBase.linkedProjectPath(it) !in projectFilesFromPreviousImport

        if (newProjectToImport) MavenProjectChanges.ALL else originalProjectsChanges.getOrDefault(it, MavenProjectChanges.NONE)
      }

    if (allProjectToImport.values.any { it.hasChanges() }) return true to allProjectToImport

    // check for a situation, when we have a newly ignored project, but no other changes
    val projectFilesToImport = allProjectToImport.keys.mapTo(CollectionFactory.createFilePathSet()) {
      WorkspaceModuleImporterBase.linkedProjectPath(it)
    }
    return (projectFilesToImport != projectFilesFromPreviousImport) to allProjectToImport
  }

  protected fun finalizeImport(modules: List<ModuleImportData>,
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

  protected data class ModuleImportData(val module: Module, val mavenProject: MavenProject, val moduleType: MavenModuleType?)
}