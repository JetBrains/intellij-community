// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.MavenDataKeys;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import javax.swing.*;

public abstract class MavenTreeAction extends MavenAction {
  @Override
  protected boolean isAvailable(@NotNull AnActionEvent e) {
    return super.isAvailable(e) && MavenActionUtil.isMavenizedProject(e.getDataContext()) && getTree(e) != null;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  protected static @Nullable JTree getTree(AnActionEvent e) {
    return e.getData(MavenDataKeys.MAVEN_PROJECTS_TREE);
  }

  public static class CollapseAll extends MavenTreeAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      JTree tree = getTree(e);
      if (tree == null) return;

      int row = tree.getRowCount() - 1;
      while (row >= 0) {
        tree.collapseRow(row);
        row--;
      }
    }
  }

  public static class ExpandAll extends MavenTreeAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      JTree tree = getTree(e);
      if (tree == null) return;

      for (int i = 0; i < tree.getRowCount(); i++) {
        tree.expandRow(i);
      }
    }
  }
}
