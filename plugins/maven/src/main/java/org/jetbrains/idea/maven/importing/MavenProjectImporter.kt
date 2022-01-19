// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.idea.maven.importing.workspaceModel.MavenProjectImporterToWorkspaceModel
import org.jetbrains.idea.maven.project.*

interface MavenProjectImporter {
  fun importProject(): List<MavenProjectsProcessorTask>?
  val createdModules: List<Module>

  companion object {
    @JvmStatic
    fun createImporter(project: Project,
                       projectsTree: MavenProjectsTree,
                       fileToModuleMapping: Map<VirtualFile, Module>,
                       projectsToImportWithChanges: Map<MavenProject, MavenProjectChanges>,
                       importModuleGroupsRequired: Boolean,
                       modelsProvider: IdeModifiableModelsProvider,
                       importingSettings: MavenImportingSettings,
                       dummyModule: Module?): MavenProjectImporter {
      if (isImportToWorkspaceModelEnabled()) {
        return MavenProjectImporterToWorkspaceModel(projectsTree, projectsToImportWithChanges, importingSettings,
                                                    VirtualFileUrlManager.getInstance(project), project)
      }
      return MavenProjectImporterImpl(project, projectsTree, fileToModuleMapping, projectsToImportWithChanges, importModuleGroupsRequired,
                                      modelsProvider, importingSettings, dummyModule)
    }

    @JvmStatic
    fun isImportToWorkspaceModelEnabled(): Boolean = Registry.`is`("maven.import.to.workspace.model")
  }
}