/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 05.12.2006
 * Time: 19:39:22
 */
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.FilterComponent;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CommittedChangesPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.committed.CommittedChangesPanel");

  private CommittedChangesBrowser myBrowser;
  private final Project myProject;
  private CommittedChangesProvider myProvider;
  private ChangeBrowserSettings mySettings;
  private VirtualFile myRoot;
  private int myMaxCount = 0;
  private FilterComponent myFilterComponent = new MyFilterComponent();
  private List<CommittedChangeList> myChangesFromProvider;

  public CommittedChangesPanel(Project project, final CommittedChangesProvider provider, final ChangeBrowserSettings settings) {
    super(new BorderLayout());
    mySettings = settings;
    myProject = project;
    myProvider = provider;
    myBrowser = new CommittedChangesBrowser(project, new CommittedChangesTableModel(new ArrayList<CommittedChangeList>()));
    add(myBrowser, BorderLayout.CENTER);

    JPanel toolbarPanel = new JPanel(new BorderLayout());
    DefaultActionGroup toolbarActionGroup = new DefaultActionGroup();
    final RefreshAction refreshAction = new RefreshAction();
    toolbarActionGroup.add(refreshAction);
    refreshAction.registerCustomShortcutSet(CommonShortcuts.getRerun(), this);
    toolbarActionGroup.add(new FilterAction());
    ActionToolbar toolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActionGroup, true);
    toolbarPanel.add(toolBar.getComponent(), BorderLayout.WEST);
    toolbarPanel.add(myFilterComponent, BorderLayout.EAST);
    myBrowser.addToolBar(toolbarPanel);
  }

  public void setRoot(final VirtualFile root) {
    myRoot = root;
  }

  public void setMaxCount(final int maxCount) {
    myMaxCount = maxCount;
  }

  public void setProvider(final CommittedChangesProvider provider) {
    if (myProvider != provider) {
      myProvider = provider;
      mySettings = provider.createDefaultSettings(); 
    }
  }

  public void refreshChanges() {
    final Ref<VcsException> refEx = new Ref<VcsException>();
    final Ref<List<CommittedChangeList>> changes = new Ref<List<CommittedChangeList>>();
    boolean completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        try {
          if (myRoot == null) {
            changes.set(myProvider.getAllCommittedChanges(mySettings, myMaxCount));
          }
          else {
            changes.set(myProvider.getCommittedChanges(mySettings, myRoot, myMaxCount));
          }
        }
        catch (VcsException ex) {
          refEx.set(ex);
        }
      }
    }, "Loading changes", true, myProject);
    if (!refEx.isNull()) {
      LOG.info(refEx.get());
      Messages.showErrorDialog(myProject, "Error refreshing view: " + StringUtil.join(refEx.get().getMessages(), "\n"), "Committed Changes");
    }
    else if (completed) {
      myChangesFromProvider = changes.get();
      updateFilteredModel();
    }
  }

  private void updateFilteredModel() {
    if (myChangesFromProvider == null) {
      return;
    }
    if (StringUtil.isEmpty(myFilterComponent.getFilter())) {
      myBrowser.setModel(new CommittedChangesTableModel(myChangesFromProvider, myProvider.getColumns()));
    }
    else {
      final String[] strings = myFilterComponent.getFilter().split(" ");
      for(int i=0; i<strings.length; i++) {
        strings [i] = strings [i].toLowerCase();
      }
      List<CommittedChangeList> filteredChanges = new ArrayList<CommittedChangeList>();
      for(CommittedChangeList changeList: myChangesFromProvider) {
        if (changeListMatches(changeList, strings)) {
          filteredChanges.add(changeList);
        }
      }
        myBrowser.setModel(new CommittedChangesTableModel(filteredChanges, myProvider.getColumns()));
    }
  }

  private static boolean changeListMatches(final CommittedChangeList changeList, final String[] filterWords) {
    for(String word: filterWords) {
      if (changeList.getComment().toLowerCase().indexOf(word) >= 0 ||
          changeList.getCommitterName().toLowerCase().indexOf(word) >= 0 ||
          Long.toString(changeList.getNumber()).indexOf(word) >= 0) {
        return true;
      }
    }
    return false;
  }

  private void setChangesFilter() {
    CommittedChangesFilterDialog filterDialog = new CommittedChangesFilterDialog(myProject, myProvider.createFilterUI(true), mySettings);
    filterDialog.show();
    if (filterDialog.isOK()) {
      mySettings = filterDialog.getSettings();
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

  private class MyFilterComponent extends FilterComponent {
    public MyFilterComponent() {
      super("COMMITTED_CHANGES_FILTER_HISTORY", 20);
    }

    public void filter() {
      updateFilteredModel();
    }
  }
}