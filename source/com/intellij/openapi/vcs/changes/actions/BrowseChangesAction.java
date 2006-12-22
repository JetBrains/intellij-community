/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vcs.changes.ui.CommittedChangesFilterDialog;
import com.intellij.openapi.vcs.changes.ui.CommittedChangesPanel;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.CommonBundle;

/**
 * @author yole
 */
public class BrowseChangesAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    VirtualFile vFile = e.getData(DataKeys.VIRTUAL_FILE);
    assert vFile != null;
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(vFile);
    final CommittedChangesProvider provider = vcs.getCommittedChangesProvider();
    assert provider != null;
    ChangeBrowserSettings settings = provider.createDefaultSettings();
    CommittedChangesFilterDialog dlg = new CommittedChangesFilterDialog(project, provider.createFilterUI(), settings);
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

    CommittedChangesPanel panel = new CommittedChangesPanel(project, provider, settings);
    panel.setRoot(vFile);
    panel.setMaxCount(maxCount);
    panel.refreshChanges();
    final ContentFactory factory = PeerFactory.getInstance().getContentFactory();
    final Content content = factory.createContent(panel, VcsBundle.message("browse.changes.content.title", vFile.getPresentableUrl()), false);
    final ChangesViewContentManager contentManager = ChangesViewContentManager.getInstance(project);
    contentManager.addContent(content);
    contentManager.setSelectedContent(content);

    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
    if (!window.isVisible()) {
      window.activate(null);
    }
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(isActionEnabled(e));
  }

  private static boolean isActionEnabled(final AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    if (project == null) return false;
    VirtualFile vFile = e.getData(DataKeys.VIRTUAL_FILE);
    if (vFile == null) return false;
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(vFile);
    return vcs != null && vcs.getCommittedChangesProvider() != null;
  }
}