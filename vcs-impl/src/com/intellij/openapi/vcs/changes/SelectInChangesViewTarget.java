/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.openapi.vcs.changes;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class SelectInChangesViewTarget implements SelectInTarget {
  private Project myProject;

  public SelectInChangesViewTarget(final Project project) {
    myProject = project;
  }

  public String toString() {
    return VcsBundle.message("changes.toolwindow.name");
  }

  public boolean canSelect(final SelectInContext context) {
    final VirtualFile file = context.getVirtualFile();
    FileStatus fileStatus = FileStatusManager.getInstance(myProject).getStatus(file);
    return ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss().length != 0 &&
           !fileStatus.equals(FileStatus.NOT_CHANGED);
  }

  public void selectIn(final SelectInContext context, final boolean requestFocus) {
    final VirtualFile file = context.getVirtualFile();
    Runnable runnable = new Runnable() {
      public void run() {
        ChangesViewContentManager.getInstance(myProject).selectContent("Local");
        ChangesViewManager.getInstance(myProject).selectFile(file);
      }
    };
    if (requestFocus) {
      ToolWindowManager.getInstance(myProject).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID).activate(runnable);
    }
    else {
      runnable.run();
    }
  }

  public String getToolWindowId() {
    return ChangesViewContentManager.TOOLWINDOW_ID;
  }

  @Nullable public String getMinorViewId() {
    return null;
  }

  public float getWeight() {
    return 9;
  }
}