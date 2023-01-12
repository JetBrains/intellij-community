// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.importing.tree.MavenProjectTreeLegacyImporter
import org.jetbrains.idea.maven.importing.workspaceModel.WorkspaceProjectImporter
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.utils.MavenLog
import java.util.concurrent.atomic.AtomicInteger

@ApiStatus.Internal
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
                       previewModule: Module?,
                       importingActivity: StructuredIdeActivity): MavenProjectImporter {
      val importer = createImporter(project, projectsTree, projectsToImportWithChanges, importModuleGroupsRequired, modelsProvider,
                                    importingSettings, previewModule)
      return object : MavenProjectImporter {
        override fun importProject(): List<MavenProjectsProcessorTask>? {
          val activity = MavenImportStats.startApplyingModelsActivity(project, importingActivity)
          val startTime = System.currentTimeMillis()
          try {
            importingInProgress.incrementAndGet()
            return importer.importProject()
          }
          finally {
            importingInProgress.decrementAndGet()
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
                               previewModule: Module?): MavenProjectImporter {
      if (isImportToWorkspaceModelEnabled(project)) {
        return WorkspaceProjectImporter(projectsTree, projectsToImportWithChanges,
                                        importingSettings, modelsProvider, project)
      }

      if (isLegacyImportToTreeStructureEnabled(project)) {
        return MavenProjectTreeLegacyImporter(project, projectsTree, projectsToImportWithChanges,
                                              modelsProvider, importingSettings)
      }

      return MavenProjectLegacyImporter(project, projectsTree,
                                        projectsToImportWithChanges,
                                        importModuleGroupsRequired,
                                        modelsProvider, importingSettings,
                                        previewModule)
    }

    @JvmStatic
    fun tryUpdateTargetFolders(project: Project) {
      if (isImportToWorkspaceModelEnabled(project)) {
        WorkspaceProjectImporter.updateTargetFolders(project)
      }
      else {
        MavenLegacyFoldersImporter.updateProjectFolders(/* project = */ project, /* updateTargetFoldersOnly = */ true)
      }
    }

    private val importingInProgress = AtomicInteger()

    @JvmStatic
    fun isImportingInProgress(): Boolean {
      return importingInProgress.get() > 0
    }

    @JvmStatic
    fun isImportToWorkspaceModelEnabled(project: Project?): Boolean {
      val property = System.getProperty("maven.import.to.workspace.model")
      if ("true" == property) return true
      if ("false" == property) return false
      if (project == null) return false
      return MavenProjectsManager.getInstance(project).importingSettings.isWorkspaceImportEnabled
    }

    @JvmStatic
    fun isLegacyImportToTreeStructureEnabled(project: Project?): Boolean {
      if (isImportToWorkspaceModelEnabled(project)) return false
      return "true" == System.getProperty("maven.import.use.tree.import")
    }
  }
}