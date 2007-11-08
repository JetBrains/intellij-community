/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.actions;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesFilterDialog;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;

/**
 * @author yole
 */
public class BrowseChangesAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    VirtualFile vFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    assert vFile != null;
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(vFile);
    assert vcs != null;
    final CommittedChangesProvider provider = vcs.getCommittedChangesProvider();
    assert provider != null;
    ChangeBrowserSettings settings = provider.createDefaultSettings();
    CommittedChangesFilterDialog dlg = new CommittedChangesFilterDialog(project, provider.createFilterUI(true), settings);
    dlg.show();
    if (!dlg.isOK()) return;

    int maxCount = 0;
    if (!settings.isAnyFilterSpecified()) {
      int rc = Messages.showDialog(project, VcsBundle.message("browse.changes.no.filter.prompt"), VcsBundle.message("browse.changes.title"),
                                   new String[] {
                                     VcsBundle.message("browse.changes.show.all.button"),
                                     VcsBundle.message("browse.changes.show.recent.button"),
                                     CommonBundle.getCancelButtonText()
                                   }, 1, Messages.getQuestionIcon());
      if (rc == 2) {
        return;
      }
      if (rc == 1) {
        maxCount = 50;
      }
    }

    AbstractVcsHelper.getInstance(project).openCommittedChangesTab(provider, vFile, settings, maxCount, null);
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(isActionEnabled(e));
  }

  private static boolean isActionEnabled(final AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return false;
    VirtualFile vFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    if (vFile == null) return false;
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(vFile);
    if (vcs == null || vcs.getCommittedChangesProvider() == null) {
      return false;
    }
    FilePath filePath = PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(vFile);
    return vcs.fileExistsInVcs(filePath);
  }
}