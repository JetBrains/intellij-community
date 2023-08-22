// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.core.fus;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.openapi.project.Project;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.actions.SwitchTaskAction;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public class TasksStateCollector extends ProjectUsagesCollector {

  private static final EventLogGroup GROUP = new EventLogGroup("tasks.state.collector", 1);

  private static final EventId1<Boolean> TASKS_COMBO_ON_TOOLBAR =
    GROUP.registerEvent("combo_on_toolbar", EventFields.Boolean("visible"));

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @Override
  protected @NotNull Set<MetricEvent> getMetrics(@NotNull Project project) {
    TaskManager taskManager = TaskManager.getManager(project);
    LocalTask activeTask = taskManager.getActiveTask();
    return Collections.singleton(
      TASKS_COMBO_ON_TOOLBAR.metric(SwitchTaskAction.isTaskManagerComboInToolbarEnabledAndVisible(activeTask, taskManager)));
  }
}
