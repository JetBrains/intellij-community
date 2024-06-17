// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.importing.workspaceModel.StaticWorkspaceProjectImporter
import org.jetbrains.idea.maven.importing.workspaceModel.WorkspaceProjectImporter
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.statistics.MavenImportCollector
import org.jetbrains.idea.maven.utils.MavenLog
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@ApiStatus.Internal
interface MavenProjectImporter {
  fun importProject(): List<MavenProjectsProcessorTask>?
  fun createdModules(): List<Module>

  companion object {
    @JvmStatic
    fun createStaticImporter(project: Project,
                             projectsTree: MavenProjectsTree,
                             projectsToImportWithChanges: Map<MavenProject, MavenProjectChanges>,
                             modelsProvider: IdeModifiableModelsProvider,
                             importingSettings: MavenImportingSettings,
                             parentImportingActivity: StructuredIdeActivity): MavenProjectImporter {

      val importer = StaticWorkspaceProjectImporter(projectsTree, projectsToImportWithChanges,
                                                    importingSettings, modelsProvider, project)
      return wrapWithFUS(project, parentImportingActivity, importer)
    }

    @JvmStatic
    fun createImporter(project: Project,
                       projectsTree: MavenProjectsTree,
                       projectsToImportWithChanges: Map<MavenProject, MavenProjectChanges>,
                       modelsProvider: IdeModifiableModelsProvider,
                       importingSettings: MavenImportingSettings,
                       previewModule: Module?,
                       parentImportingActivity: StructuredIdeActivity): MavenProjectImporter {
      val importer = createImporter(project, projectsTree, projectsToImportWithChanges,
                                    modelsProvider, importingSettings, previewModule)
      return wrapWithFUS(project, parentImportingActivity, importer)
    }

    private fun wrapWithFUS(project: Project,
                            parentImportingActivity: StructuredIdeActivity,
                            importer: MavenProjectImporter): MavenProjectImporter {
      return object : MavenProjectImporter {
        override fun importProject(): List<MavenProjectsProcessorTask> {
          val activity = MavenImportStats.startApplyingModelsActivity(project, parentImportingActivity)
          val startTime = System.currentTimeMillis()
          try {
            importingInProgress.incrementAndGet()

            val postImportTasks = importer.importProject()!!

            val statsMarker = PostImportingTaskMarker(parentImportingActivity)
            return ContainerUtil.concat(listOf(statsMarker.createStartedTask()),
                                        postImportTasks,
                                        listOf(statsMarker.createFinishedTask()))
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

    class PostImportingTaskMarker(importingActivity: StructuredIdeActivity) {
      private val activityId = MavenImportCollector.ACTIVITY_ID.with(importingActivity)
      private var startedNano = 0L

      fun createStartedTask(): MavenProjectsProcessorTask {
        return object : MavenProjectsProcessorTask {
          override fun perform(project: Project, embeddersManager: MavenEmbeddersManager, indicator: ProgressIndicator) {
            startedNano = System.nanoTime()
          }
        }
      }

      fun createFinishedTask(): MavenProjectsProcessorTask {
        return object : MavenProjectsProcessorTask {
          override fun perform(project: Project, embeddersManager: MavenEmbeddersManager, indicator: ProgressIndicator) {
            if (startedNano == 0L) {
              MavenLog.LOG.error("'Finished' post import task called before 'started' task")
            }
            else {
              val totalNano = System.nanoTime() - startedNano
              MavenImportCollector.POST_IMPORT_TASKS_RUN.log(project, activityId.data, TimeUnit.NANOSECONDS.toMillis(totalNano))
            }
          }
        }
      }
    }

    private fun createImporter(project: Project,
                               projectsTree: MavenProjectsTree,
                               projectsToImportWithChanges: Map<MavenProject, MavenProjectChanges>,
                               modelsProvider: IdeModifiableModelsProvider,
                               importingSettings: MavenImportingSettings,
                               previewModule: Module?): MavenProjectImporter {
      return WorkspaceProjectImporter(projectsTree, projectsToImportWithChanges, importingSettings, modelsProvider, project)
    }

    @JvmStatic
    fun tryUpdateTargetFolders(project: Project) {
      WorkspaceProjectImporter.updateTargetFolders(project)
    }

    private val importingInProgress = AtomicInteger()
  }
}