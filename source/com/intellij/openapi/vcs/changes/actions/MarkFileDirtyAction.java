/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public class MarkFileDirtyAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    final VirtualFile file = e.getData(DataKeys.VIRTUAL_FILE);
    if (file != null) {
      VcsDirtyScopeManager.getInstance(project).fileDirty(file);
    }
  }
}