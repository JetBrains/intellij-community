// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;
import org.jetbrains.idea.maven.utils.actions.MavenToggleAction;

public abstract class MavenProjectsNavigatorAction extends MavenToggleAction {
  @Override
  protected boolean doIsSelected(AnActionEvent e) {
    final MavenProjectsNavigator navigator = getNavigator(e);
    return navigator!= null && isSelected(navigator);
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    final MavenProjectsNavigator navigator = getNavigator(e);
    if (navigator != null) {
      setSelected(navigator, state);
    }
  }

  private static @Nullable MavenProjectsNavigator getNavigator(AnActionEvent e) {
    final Project project = MavenActionUtil.getProject(e.getDataContext());
    return project != null ? MavenProjectsNavigator.getInstance(project) : null;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  protected abstract boolean isSelected(@NotNull MavenProjectsNavigator navigator);

  protected abstract void setSelected(@NotNull MavenProjectsNavigator navigator, boolean value);
}
