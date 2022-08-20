// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing;

import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.openapi.externalSystem.statistics.ExternalSystemStatUtilKt;
import com.intellij.openapi.externalSystem.statistics.ProjectImportCollector;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.Collections;

public class MavenImportStats {
  // Hacky way to measure report import stages speed.
  // The better way would be to use StructuredIdeActivity.stageStared in MavenImportingManager.doImport with the new importing Flow

  @NotNull
  public static StructuredIdeActivity startImportActivity(Project project) {
    return ExternalSystemStatUtilKt.importActivityStarted(project, MavenUtil.SYSTEM_ID, () ->
      Collections.singletonList(ProjectImportCollector.TASK_CLASS.with(ImportingTask.class))
    );
  }

  @NotNull
  public static StructuredIdeActivity startApplyingModelsActivity(Project project, StructuredIdeActivity importingActivity) {
    return ProjectImportCollector.IMPORT_STAGE.startedWithParent(project, importingActivity, () -> Collections.singletonList(
      ProjectImportCollector.TASK_CLASS.with(ApplyingModelTask.class)));
  }

  public static class WrapperTask {

  }

  public static class ReadingTask {

  }

  public static class ResolvingTask {

  }

  public static class PluginsResolvingTask {

  }

  public static class ImportingTask {
  }

  public static class ImportingTaskOld {
  }

  public static class ApplyingModelTask {
  }

  public static class ConfiguringProjectsTask {
  }
}
