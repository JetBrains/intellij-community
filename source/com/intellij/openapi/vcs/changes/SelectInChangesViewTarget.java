/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.openapi.vcs.changes;

import com.intellij.ide.StandardTargetWeights;
import com.intellij.ide.impl.SelectInTargetPsiWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * @author yole
 */
public class SelectInChangesViewTarget extends SelectInTargetPsiWrapper {
  protected SelectInChangesViewTarget(final Project project) {
    super(project);
  }

  public String toString() {
    return VcsBundle.message("changes.toolwindow.name");
  }

  public String getToolWindowId() {
    return ChangesViewManager.TOOLWINDOW_ID;
  }

  public String getMinorViewId() {
    return null;
  }

  public float getWeight() {
    return StandardTargetWeights.CHANGES_VIEW;
  }

  protected boolean canSelect(PsiFile file) {
    return ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss().length != 0 &&
           !file.getFileStatus().equals(FileStatus.NOT_CHANGED);
  }

  protected void select(final Object selector, VirtualFile virtualFile, final boolean requestFocus) {
  }

  protected boolean canWorkWithCustomObjects() {
    return false;
  }

  protected void select(final PsiElement element, boolean requestFocus) {
    Runnable runnable = new Runnable() {
      public void run() {
        ChangesViewManager.getInstance(myProject).selectFile(element);
      }
    };
    if (requestFocus) {
      ToolWindowManager.getInstance(myProject).getToolWindow(ChangesViewManager.TOOLWINDOW_ID).activate(runnable);
    }
    else {
      runnable.run();
    }
  }
}