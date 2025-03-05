// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.dialogs.CopiesPanel;

import javax.swing.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class WorkingCopiesContent implements ChangesViewContentProvider {
  private final @NotNull Project myProject;

  public WorkingCopiesContent(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public JComponent initContent() {
    return new CopiesPanel(myProject);
  }

  public static void show(@NotNull Project project) {
    final ToolWindowManager manager = ToolWindowManager.getInstance(project);
    final ToolWindow window = manager.getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
    if (window != null) {
      window.show(null);
      final ContentManager cm = window.getContentManager();
      final Content content = cm.findContent(getTabName());
      if (content != null) {
        cm.setSelectedContent(content, true);
      }
    }
  }

  static final class VisibilityPredicate implements Predicate<Project> {
    @Override
    public boolean test(@NotNull Project project) {
      return ProjectLevelVcsManager.getInstance(project).checkVcsIsActive(SvnVcs.VCS_NAME);
    }
  }

  static final class DisplayNameSupplier implements Supplier<String> {
    @Override
    public String get() {
      return SvnBundle.message("toolwindow.working.copies.info.title");
    }
  }

  public static @NotNull String getTabName() {
    return SvnBundle.message("dialog.show.svn.map.title");
  }
}
