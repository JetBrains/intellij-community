// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
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
      return MavenProjectImporterImpl(project, projectsTree, fileToModuleMapping, projectsToImportWithChanges, importModuleGroupsRequired,
                                      modelsProvider, importingSettings, dummyModule)
    }

  }
}