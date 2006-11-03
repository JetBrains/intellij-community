/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.11.2006
 * Time: 22:12:19
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ui.RollbackChangesDialog;

import java.util.Arrays;
import java.util.List;

public class RollbackAction extends AnAction {
  public void update(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    Change[] changes = e.getData(DataKeys.CHANGES);
    List<FilePath> files = e.getData(ChangesListView.MISSING_FILES_DATA_KEY);
    e.getPresentation().setEnabled(ChangesUtil.getChangeListIfOnlyOne(project, changes) != null ||
                                   (files != null && !files.isEmpty()));
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    List<FilePath> files = e.getData(ChangesListView.MISSING_FILES_DATA_KEY);
    if (files != null && !files.isEmpty()) {
      new RollbackDeletionAction().actionPerformed(e);
    }
    else {
      Change[] changes = e.getData(DataKeys.CHANGES);
      final ChangeList list = ChangesUtil.getChangeListIfOnlyOne(project, changes);
      if (list == null) return;

      FileDocumentManager.getInstance().saveAllDocuments();
      RollbackChangesDialog.rollbackChanges(project, Arrays.asList(changes));
    }
  }
}