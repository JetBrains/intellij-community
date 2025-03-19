// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import org.jetbrains.idea.maven.importing.MavenImporter
import org.jetbrains.idea.maven.importing.MavenProjectImporterUtil
import org.jetbrains.idea.maven.importing.MavenStaticSyncAware
import org.jetbrains.idea.maven.importing.MavenWorkspaceConfigurator
import org.jetbrains.idea.maven.project.*

internal class StaticWorkspaceProjectImporter(
  projectsTree: MavenProjectsTree,
  projectsToImport: List<MavenProject>,
  importingSettings: MavenImportingSettings,
  modifiableModelsProvider: IdeModifiableModelsProvider,
  project: Project
) : WorkspaceProjectImporter(projectsTree, projectsToImport, importingSettings, modifiableModelsProvider, project) {

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
        MavenProjectImporterUtil.LegacyExtensionImporter.createIfApplicable(projectWithModules.mavenProject,
                                                                            moduleWithType.module,
                                                                            moduleWithType.type,
                                                                            myProjectsTree,
                                                                            projectWithModules.hasChanges,
                                                                            moduleNameByProject,
                                                                            importers)
      }
    }
    MavenProjectImporterUtil.importLegacyExtensions(myProject, myModifiableModelsProvider, legacyFacetImporters, postTasks, activity)
  }
}