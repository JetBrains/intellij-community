package com.jetbrains.edu.learning;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.learning.actions.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class StudyBasePluginConfigurator implements StudyPluginConfigurator {
  @NotNull
  @Override
  public DefaultActionGroup getActionGroup(Project project) {
    return getDefaultActionGroup();
  }

  @NotNull
  public static DefaultActionGroup getDefaultActionGroup() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new StudyPreviousTaskAction());
    group.add(new StudyNextTaskAction());
    group.add(new StudyRefreshTaskFileAction());
    group.add(new StudyShowHintAction());

    group.add(new StudyRunAction());
    group.add(new StudyEditInputAction());
    return group;
  }

  @Nullable
  @Override
  public StudyAfterCheckAction[] getAfterCheckActions() {
    return null;
  }


}
