// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull private final Project myProject;

  public WorkingCopiesContent(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public JComponent initContent() {
    return new CopiesPanel(myProject);
  }

  public static void show(@NotNull Project project) {
    final ToolWindowManager manager = ToolWindowManager.getInstance(project);
    if (manager != null) {
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
  }

  final static class VisibilityPredicate implements Predicate<Project> {
    @NotNull
    @Override
    public boolean test(@NotNull Project project) {
      return ProjectLevelVcsManager.getInstance(project).checkVcsIsActive(SvnVcs.VCS_NAME);
    }
  }

  final static class DisplayNameSupplier implements Supplier<String> {
    @Override
    public String get() {
      return SvnBundle.message("toolwindow.working.copies.info.title");
    }
  }

  @NotNull
  public static String getTabName() {
    return SvnBundle.message("dialog.show.svn.map.title");
  }
}
