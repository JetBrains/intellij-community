/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.11.2006
 * Time: 22:09:59
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.changes.ChangesViewManager;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.util.List;

public abstract class AbstractMissingFilesAction extends AnAction {

  public void update(AnActionEvent e) {
    List<FilePath> files = e.getData(ChangesListView.MISSING_FILES_DATA_KEY);
    boolean enabled = files != null && !files.isEmpty();
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(enabled);
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    final List<FilePath> files = e.getData(ChangesListView.MISSING_FILES_DATA_KEY);
    if (files == null) return;

    ChangesUtil.processFilePathsByVcs(project, files, new ChangesUtil.PerVcsProcessor<FilePath>() {
      public void process(final AbstractVcs vcs, final List<FilePath> items) {
        final CheckinEnvironment environment = vcs.getCheckinEnvironment();
        if (environment != null) {
          processFiles(environment, files);
        }
      }
    });

    for (FilePath file : files) {
      VcsDirtyScopeManager.getInstance(project).fileDirty(file);
    }
    ChangesViewManager.getInstance(project).scheduleRefresh();
  }

  protected abstract void processFiles(final CheckinEnvironment environment, final List<FilePath> files);
}