// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import org.jetbrains.idea.maven.importing.*
import org.jetbrains.idea.maven.project.*

internal class StaticWorkspaceProjectImporter(
  projectsTree: MavenProjectsTree,
  projectsToImportWithChanges: Map<MavenProject, MavenProjectChanges>,
  importingSettings: MavenImportingSettings,
  modifiableModelsProvider: IdeModifiableModelsProvider,
  project: Project
) : WorkspaceProjectImporter(projectsTree, projectsToImportWithChanges, importingSettings, modifiableModelsProvider, project) {

  override fun workspaceConfigurators(): List<MavenWorkspaceConfigurator> {
    return super.workspaceConfigurators().filter { it is MavenStaticSyncAware }
  }

  override fun addAfterImportTask(postTasks: ArrayList<MavenProjectsProcessorTask>,
                                  contextData: UserDataHolderBase,
                                  appliedProjectsWithModules: List<MavenProjectWithModulesData<Module>>) {
  }

  override fun configLegacyFacets(mavenProjectsWithModules: List<MavenProjectWithModulesData<Module>>,
                                  moduleNameByProject: Map<MavenProject, String>,
                                  postTasks: List<MavenProjectsProcessorTask>,
                                  activity: StructuredIdeActivity) {

    val legacyFacetImporters = mavenProjectsWithModules.flatMap { projectWithModules ->
      projectWithModules.modules.asSequence().mapNotNull { moduleWithType ->
        val importers = MavenImporter
          .getSuitableImporters(projectWithModules.mavenProject, true)
          .filter { it is MavenStaticSyncAware }
        MavenLegacyModuleImporter.ExtensionImporter.createIfApplicable(projectWithModules.mavenProject,
                                                                       moduleWithType.module,
                                                                       moduleWithType.type,
                                                                       myProjectsTree,
                                                                       projectWithModules.changes,
                                                                       moduleNameByProject,
                                                                       importers)
      }
    }
    MavenProjectImporterBase.importExtensions(myProject, myModifiableModelsProvider, legacyFacetImporters, postTasks, activity)
  }
}