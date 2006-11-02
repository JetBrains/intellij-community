/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.11.2006
 * Time: 21:53:06
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.project.Project;

import java.util.Arrays;

public class CommitAction extends AnAction {
  public CommitAction() {
    super(VcsBundle.message("changes.action.commit.text"), VcsBundle.message("changes.action.commit.description"),
          IconLoader.getIcon("/actions/commit.png"));
  }

  public void update(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    Change[] changes = e.getData(DataKeys.CHANGES);
    e.getPresentation().setEnabled(ChangesUtil.getChangeListIfOnlyOne(project, changes) != null);
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    Change[] changes = e.getData(DataKeys.CHANGES);
    final ChangeList list = ChangesUtil.getChangeListIfOnlyOne(project, changes);
    if (list == null) return;

    CommitChangeListDialog.commitChanges(project, Arrays.asList(changes), list,
                                         ChangeListManager.getInstance(project).getRegisteredExecutors());
  }
}