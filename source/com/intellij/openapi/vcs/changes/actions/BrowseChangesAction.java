/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 05.12.2006
 * Time: 18:55:09
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.changes.ui.CommittedChangesFilterDialog;
import com.intellij.openapi.vcs.changes.ui.CommittedChangesPanel;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;

public class BrowseChangesAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    VirtualFile vFile = e.getData(DataKeys.VIRTUAL_FILE);
    assert vFile != null;
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(vFile);
    final CommittedChangesProvider provider = vcs.getCommittedChangesProvider();
    assert provider != null;
    CommittedChangesFilterDialog dlg = new CommittedChangesFilterDialog(project, provider.createFilterUI());
    dlg.show();
    if (!dlg.isOK()) return;
    CommittedChangesPanel panel = new CommittedChangesPanel(project, provider);
    panel.setRoot(vFile);
    panel.refreshChanges();
    final ContentFactory factory = PeerFactory.getInstance().getContentFactory();
    final Content content = factory.createContent(panel, "Changes under " + vFile.getPresentableUrl(), false);
    final ChangesViewContentManager contentManager = ChangesViewContentManager.getInstance(project);
    contentManager.addContent(content);
    contentManager.setSelectedContent(content);    
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