package com.intellij.lifecycle;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;


public class ControlledAlarmFactory {
  private ControlledAlarmFactory() {
  }

  public static SlowlyClosingAlarm createOnOwnThread(@NotNull final Project project, @NotNull final String name) {
    return new SlowlyClosingAlarm(project, name);
  }

  public static SlowlyClosingAlarm createOnSharedThread(@NotNull final Project project, @NotNull final String name,
                                                        final @NotNull ExecutorService executor) {
    return new SlowlyClosingAlarm(project, name, executor, true);
  }

  public static ScheduledSlowlyClosingAlarm createScheduledOnSharedThread(@NotNull final Project project, @NotNull final String name,
                                                        final @NotNull ScheduledExecutorService executor) {
    return new ScheduledSlowlyClosingAlarm(project, name, executor, true);
  }
}
