/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 30.11.2006
 * Time: 18:12:47
 */
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CommittedChangesViewManager implements ProjectComponent {
  private ProjectLevelVcsManager myVcsManager;
  private ChangesViewContentManager myContentManager;
  private CommittedChangesBrowser myBrowser;
  private JPanel myComponent;
  private Content myContent;
  private Project myProject;
  private CommittedChangesProvider myProvider = null;

  public CommittedChangesViewManager(final Project project, final ChangesViewContentManager contentManager,
                                     final ProjectLevelVcsManager vcsManager) {
    myProject = project;
    myContentManager = contentManager;
    myVcsManager = vcsManager;
  }

  public void projectOpened() {
    buildComponent();
    updateChangesContent();
  }

  public void projectClosed() {
  }

  @NonNls @NotNull
  public String getComponentName() {
    return "CommittedChangesViewManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private void updateChangesContent() {
    final AbstractVcs[] vcss = myVcsManager.getAllActiveVcss();
    for(AbstractVcs vcs: vcss) {
      if (vcs.getCommittedChangesProvider() != null) {
        myProvider = vcs.getCommittedChangesProvider();
        break;
      }
    }
    if (myProvider == null) {
      if (myContent != null) {
        myContentManager.removeContent(myContent);
      }
      myContent = null;
    }
    else {
      if (myContent == null) {
        if (myComponent == null) {
          buildComponent();
        }
        myContent = PeerFactory.getInstance().getContentFactory().createContent(myComponent, "Committed", false);
        myContentManager.addContent(myContent);
      }
    }
  }

  private void buildComponent() {
    myBrowser = new CommittedChangesBrowser(myProject, new CommittedChangesTableModel(new ArrayList<CommittedChangeList>()));
    myComponent = new JPanel(new BorderLayout());
    myComponent.add(myBrowser, BorderLayout.CENTER);

    DefaultActionGroup toolbarActionGroup = new DefaultActionGroup();
    toolbarActionGroup.add(new RefreshAction());
    toolbarActionGroup.add(new FilterAction());
    ActionToolbar toolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActionGroup, true);
    myBrowser.addToolBar(toolBar.getComponent());
    //myComponent.add(toolBar.getComponent(), BorderLayout.WEST);
  }

  private void refreshChanges() {
    try {
      final List<CommittedChangeList> list = myProvider.getAllCommittedChanges(50);
      myBrowser.setModel(new CommittedChangesTableModel(list, myProvider.getColumns()));
    }
    catch (VcsException ex) {
      Messages.showErrorDialog(myProject, "Error refreshing view: " + ex.getMessages(), "Committed Changes");
    }
  }

  private void setChangesFilter() {
    CommittedChangesFilterDialog filterDialog = new CommittedChangesFilterDialog(myProject, myProvider.createFilterUI());
    filterDialog.show();
    if (filterDialog.isOK()) {
      refreshChanges();
    }
  }

  private class RefreshAction extends AnAction {
    public RefreshAction() {
      super("Refresh", "Refresh the list of committed changes", IconLoader.getIcon("/vcs/refresh.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      refreshChanges();
    }
  }

  private class FilterAction extends AnAction {
    public FilterAction() {
      super("Filter", "Change filtering criteria", IconLoader.getIcon("/ant/filter.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      setChangesFilter();
    }
  }
}