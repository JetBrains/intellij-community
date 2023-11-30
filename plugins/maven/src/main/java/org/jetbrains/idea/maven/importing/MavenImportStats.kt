// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.internal.statistic.IdeActivityDefinition
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.statistics.ExternalSystemActionsCollector
import com.intellij.openapi.externalSystem.statistics.ProjectImportCollector
import com.intellij.openapi.externalSystem.statistics.anonymizeSystemId
import com.intellij.openapi.project.Project

object MavenImportStats {
  // Hacky way to measure report import stages speed.
  fun startApplyingModelsActivity(project: Project?, importingActivity: StructuredIdeActivity?): StructuredIdeActivity {
    return ProjectImportCollector.WORKSPACE_APPLY_STAGE.startedWithParent(project, importingActivity!!)
  }


  sealed class MavenSyncSubstask(val activity: IdeActivityDefinition)
  sealed class MavenBackgroundActivitySubstask(val activity: IdeActivityDefinition)

  data object ReadingTask : MavenSyncSubstask(ProjectImportCollector.READ_STAGE)

  data object ResolvingTask : MavenSyncSubstask(ProjectImportCollector.RESOLVE_STAGE)

  data object PluginsResolvingTask : MavenBackgroundActivitySubstask(ProjectImportCollector.PLUGIN_RESOLVE_PROCESS)


  @Deprecated("to be removed")
  class ImportingTaskOld

  @Deprecated("to be removed")
  class ImportingTask

  data object ApplyingModelTask : MavenSyncSubstask(ProjectImportCollector.WORKSPACE_APPLY_STAGE)

  data object ConfiguringProjectsTask : MavenSyncSubstask(ProjectImportCollector.PROJECT_CONFIGURATION_STAGE)

  class MavenSyncProjectTask

  class MavenReapplyModelOnlyProjectTask
}


fun importActivityStarted(project: Project, externalSystemId: ProjectSystemId): StructuredIdeActivity {
  return importActivityStarted(project, externalSystemId, ProjectImportCollector.IMPORT_ACTIVITY)
}

fun importActivityStarted(project: Project, externalSystemId: ProjectSystemId, definition: IdeActivityDefinition): StructuredIdeActivity {
  return definition.started(project) {
    val data: MutableList<EventPair<*>> = mutableListOf(
      ExternalSystemActionsCollector.EXTERNAL_SYSTEM_ID.with(anonymizeSystemId(externalSystemId)))
    data
  }
}


fun <T> runImportActivitySync(project: Project,
                              externalSystemId: ProjectSystemId,
                              taskClass: Class<*>,
                              action: () -> T): T {
  val activity = importActivityStarted(project, externalSystemId)
  try {
    return action()
  }
  finally {
    activity.finished()
  }
}
