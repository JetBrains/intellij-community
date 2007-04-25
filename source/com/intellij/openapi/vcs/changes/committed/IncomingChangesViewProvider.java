package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.util.messages.MessageBus;

import javax.swing.*;
import java.util.List;

/**
 * @author yole
 */
public class IncomingChangesViewProvider implements ChangesViewContentProvider {
  private Project myProject;
  private MessageBus myBus;
  private CommittedChangesBrowser myBrowser;

  public IncomingChangesViewProvider(final Project project, final MessageBus bus) {
    myProject = project;
    myBus = bus;
  }

  public JComponent initContent() {
    final List<CommittedChangeList> list = CommittedChangesCache.getInstance(myProject).getIncomingChanges();
    myBrowser = new CommittedChangesBrowser(myProject, new CommittedChangesTableModel(list));
    ActionGroup group = (ActionGroup) ActionManager.getInstance().getAction("IncomingChangesToolbar");
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    myBrowser.addToolBar(toolbar.getComponent());
    myBus.connect().subscribe(CommittedChangesCache.COMMITTED_TOPIC, new MyCommittedChangesListener());
    return myBrowser;
  }

  private void updateModel() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        myBrowser.setItems(CommittedChangesCache.getInstance(myProject).getIncomingChanges());
      }
    });
  }

  private class MyCommittedChangesListener implements CommittedChangesListener {
    public void changesLoaded(final RepositoryLocation location, final List<CommittedChangeList> changes) {
      updateModel();
    }

    public void incomingChangesUpdated() {
      updateModel();
    }
  }
}
