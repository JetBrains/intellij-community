// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import org.jetbrains.idea.maven.importing.MavenStaticSyncAware
import org.jetbrains.idea.maven.importing.MavenWorkspaceConfigurator
import org.jetbrains.idea.maven.project.*

internal class StaticWorkspaceProjectImporter(
  projectsTree: MavenProjectsTree,
  projectsToImportWithChanges: Map<MavenProject, MavenProjectChanges>,
  importingSettings: MavenImportingSettings,
  modifiableModelsProvider: IdeModifiableModelsProvider,
  project: Project
) : WorkspaceProjectImporter(projectsTree, projectsToImportWithChanges, importingSettings, modifiableModelsProvider, project) {

  override fun workspaceConfigurators(): List<MavenWorkspaceConfigurator> {
    return super.workspaceConfigurators()
  }

  override fun addAfterImportTask(postTasks: ArrayList<MavenProjectsProcessorTask>,
                                  contextData: UserDataHolderBase,
                                  appliedProjectsWithModules: List<MavenProjectWithModulesData<Module>>) {
  }
}