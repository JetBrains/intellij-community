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
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.ui.ChangeListChooser;

import java.util.List;

public class UnshelveChangesAction extends AnAction {
  private Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.shelf.UnshelveChangesAction");

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final ShelvedChangeList[] changeLists = e.getData(ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY);
    List<ShelvedChange> changes = e.getData(ShelvedChangesViewManager.SHELVED_CHANGE_KEY);
    List<ShelvedBinaryFile> binaryFiles = e.getData(ShelvedChangesViewManager.SHELVED_BINARY_FILE_KEY);
    if (changes != null && binaryFiles != null && changes.size() == 0 && binaryFiles.size() == 0) {
      changes = null;
      binaryFiles = null;
    }
    LOG.assertTrue(changeLists != null);

    final ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    final List<LocalChangeList> allChangeLists = changeListManager.getChangeListsCopy();
    final LocalChangeList defaultChangeList = changeListManager.getDefaultChangeList();
    String defaultName = changeLists.length == 1 ? changeLists [0].DESCRIPTION : null;
    ChangeListChooser chooser = new ChangeListChooser(project, allChangeLists, defaultChangeList,
                                                      VcsBundle.message("unshelve.changelist.chooser.title"), 
                                                      defaultName);
    chooser.show();
    if (!chooser.isOK()) {
      return;
    }

    FileDocumentManager.getInstance().saveAllDocuments();

    for(ShelvedChangeList changeList: changeLists) {
      ShelveChangesManager.getInstance(project).unshelveChangeList(changeList, changes, binaryFiles, chooser.getSelectedList());
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final ShelvedChangeList[] changes = e.getData(ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY);
    e.getPresentation().setEnabled(project != null && changes != null);
  }
}