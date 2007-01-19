/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 23.11.2006
 * Time: 17:20:10
 */
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileEditor.FileDocumentManager;

import java.util.List;

public class UnshelveChangesAction extends AnAction {
  private Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.shelf.UnshelveChangesAction");

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    final ShelvedChangeList[] changeLists = e.getData(ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY);
    final List<ShelvedChange> changes = e.getData(ShelvedChangesViewManager.SHELVED_CHANGE_KEY);
    LOG.assertTrue(changeLists != null);
    FileDocumentManager.getInstance().saveAllDocuments();
    for(ShelvedChangeList changeList: changeLists) {
      ShelveChangesManager.getInstance(project).unshelveChangeList(changeList, changes);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    final ShelvedChangeList[] changes = e.getData(ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY);
    e.getPresentation().setEnabled(project != null && changes != null);
  }
}