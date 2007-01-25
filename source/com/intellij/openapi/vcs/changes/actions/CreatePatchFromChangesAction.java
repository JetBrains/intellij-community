/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitSession;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager;
import com.intellij.openapi.vcs.changes.patch.CreatePatchCommitExecutor;
import com.intellij.openapi.vcs.changes.ui.SessionDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class CreatePatchFromChangesAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    Change[] changes = e.getData(DataKeys.CHANGES);
    String commitMessage = "";
    ShelvedChangeList[] changeLists = e.getData(ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY);
    if (changeLists != null && changeLists.length > 0) {
      commitMessage = changeLists [0].DESCRIPTION;
    }
    List<Change> changeCollection = new ArrayList<Change>();
    Collections.addAll(changeCollection, changes);
    final CreatePatchCommitExecutor executor = CreatePatchCommitExecutor.getInstance(project);
    CommitSession commitSession = executor.createCommitSession();
    DialogWrapper sessionDialog = new SessionDialog(executor.getActionText(),
                                                    project,
                                                    commitSession,
                                                    changeCollection,
                                                    commitMessage);
    sessionDialog.show();
    if (!sessionDialog.isOK()) {
      return;
    }
    commitSession.execute(changeCollection, commitMessage);
  }

  public void update(final AnActionEvent e) {
    Change[] changes = e.getData(DataKeys.CHANGES);
    e.getPresentation().setEnabled(changes != null && changes.length > 0);
  }
}