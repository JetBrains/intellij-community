// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.idea.maven.importing.tree.MavenProjectTreeLegacyImporter
import org.jetbrains.idea.maven.importing.workspaceModel.WorkspaceProjectImporter
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.utils.MavenLog

interface MavenProjectImporter {
  fun importProject(): List<MavenProjectsProcessorTask>?
  fun createdModules(): List<Module>

  companion object {
    @JvmStatic
    fun createImporter(project: Project,
                       projectsTree: MavenProjectsTree,
                       projectsToImportWithChanges: Map<MavenProject, MavenProjectChanges>,
                       importModuleGroupsRequired: Boolean,
                       modelsProvider: IdeModifiableModelsProvider,
                       importingSettings: MavenImportingSettings,
                       dummyModule: Module?,
                       importingActivity: StructuredIdeActivity): MavenProjectImporter {
      val importer = createImporter(project, projectsTree, projectsToImportWithChanges, importModuleGroupsRequired, modelsProvider,
                                    importingSettings, dummyModule)
      return object : MavenProjectImporter {
        override fun importProject(): List<MavenProjectsProcessorTask>? {
          val activity = MavenImportStats.startApplyingModelsActivity(project, importingActivity)
          val startTime = System.currentTimeMillis()
          try {
            return importer.importProject()
          }
          finally {
            activity.finished()
            MavenLog.LOG.info(
              "[maven import] applying models to workspace model took ${System.currentTimeMillis() - startTime}ms")

          }
        }

        override fun createdModules(): List<Module> {
          return importer.createdModules()
        }
      }
    }

    private fun createImporter(project: Project,
                               projectsTree: MavenProjectsTree,
                               projectsToImportWithChanges: Map<MavenProject, MavenProjectChanges>,
                               importModuleGroupsRequired: Boolean,
                               modelsProvider: IdeModifiableModelsProvider,
                               importingSettings: MavenImportingSettings,
                               dummyModule: Module?): MavenProjectImporter {
      if (isImportToWorkspaceModelEnabled()) {
        return WorkspaceProjectImporter(projectsTree, projectsToImportWithChanges,
                                        importingSettings, modelsProvider, project)
      }

      if (isLegacyImportToTreeStructureEnabled(project)) {
        return MavenProjectTreeLegacyImporter(project, projectsTree, projectsToImportWithChanges,
                                              modelsProvider, importingSettings)
      }

      return MavenProjectImporterImpl(project, projectsTree, projectsToImportWithChanges, importModuleGroupsRequired,
                                      modelsProvider, importingSettings, dummyModule)
    }

    @JvmStatic
    fun tryUpdateTargetFolders(project: Project) {
      if (isImportToWorkspaceModelEnabled()) {
        WorkspaceProjectImporter.tryUpdateTargetFolders(project)
      }
      else {
        MavenLegacyFoldersImporter.updateProjectFolders(/* project = */ project, /* updateTargetFoldersOnly = */ true)
      }
    }

    @JvmStatic
    fun isImportToWorkspaceModelEnabled(): Boolean = Registry.`is`("maven.import.to.workspace.model")

    @JvmStatic
    fun isLegacyImportToTreeStructureEnabled(project: Project?): Boolean {
      if (isImportToWorkspaceModelEnabled()) return false
      if ("true" == System.getProperty("maven.import.use.tree.import")) return true
      if (project == null) return false
      return MavenProjectsManager.getInstance(project).importingSettings.isImportToTreeStructure
    }
  }
}