// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public class MavenActionGroup extends DefaultActionGroup implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    boolean available = isAvailable(e);
    e.getPresentation().setEnabledAndVisible(available);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
  protected boolean isAvailable(@NotNull AnActionEvent e) {
    return !MavenActionUtil.getMavenProjects(e.getDataContext()).isEmpty();
  }
}
