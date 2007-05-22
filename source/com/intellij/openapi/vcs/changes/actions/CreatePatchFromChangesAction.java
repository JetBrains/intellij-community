/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager;
import com.intellij.openapi.vcs.changes.patch.CreatePatchCommitExecutor;
import com.intellij.openapi.vcs.changes.ui.SessionDialog;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.progress.ProgressManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class CreatePatchFromChangesAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    final Change[] changes = e.getData(DataKeys.CHANGES);
    String commitMessage = "";
    ShelvedChangeList[] shelvedChangeLists = e.getData(ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY);
    if (shelvedChangeLists != null && shelvedChangeLists.length > 0) {
      commitMessage = shelvedChangeLists [0].DESCRIPTION;
    }
    else {
      ChangeList[] changeLists = e.getData(DataKeys.CHANGE_LISTS);
      if (changeLists != null && changeLists.length > 0) {
        commitMessage = changeLists [0].getComment();
      }
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
    // to avoid multiple progress dialogs, preload content under one progress
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        for(Change change: changes) {
          checkLoadContent(change.getBeforeRevision());
          checkLoadContent(change.getAfterRevision());
        }
      }

      private void checkLoadContent(final ContentRevision revision) {
        if (revision != null && !(revision instanceof BinaryContentRevision)) {
          try {
            revision.getContent();
          }
          catch (VcsException e1) {
            // ignore at the moment
          }
        }
      }
    }, VcsBundle.message("create.patch.loading.content.progress"), false, project);
    commitSession.execute(changeCollection, commitMessage);
  }

  public void update(final AnActionEvent e) {
    Change[] changes = e.getData(DataKeys.CHANGES);
    ChangeList[] changeLists = e.getData(DataKeys.CHANGE_LISTS);
    e.getPresentation().setEnabled(changes != null && changes.length > 0 &&
                                   (changeLists == null || changeLists.length == 1));
  }
}