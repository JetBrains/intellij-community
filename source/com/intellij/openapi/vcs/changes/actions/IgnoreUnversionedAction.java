/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 20.12.2006
 * Time: 17:17:50
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ui.IgnoreUnversionedDialog;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.IgnoredFileBean;
import com.intellij.openapi.project.Project;

import java.util.List;

public class IgnoreUnversionedAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    final List<VirtualFile> files = e.getData(ChangesListView.UNVERSIONED_FILES_DATA_KEY);
    if (files == null) return;

    IgnoreUnversionedDialog dlg = new IgnoreUnversionedDialog(project);
    dlg.setFilesToIgnore(files);
    dlg.show();
    if (!dlg.isOK()) {
      return;
    }
    final IgnoredFileBean[] ignoredFiles = dlg.getSelectedIgnoredFiles();
    if (ignoredFiles.length > 0) {
      ChangeListManager.getInstance(project).addFilesToIgnore(ignoredFiles);
    }
  }

  public void update(AnActionEvent e) {
    List<VirtualFile> files = e.getData(ChangesListView.UNVERSIONED_FILES_DATA_KEY);
    boolean enabled = files != null && !files.isEmpty();
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(enabled);
  }
}