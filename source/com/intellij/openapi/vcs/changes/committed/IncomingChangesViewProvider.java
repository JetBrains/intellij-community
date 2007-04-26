package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;

import javax.swing.*;
import java.util.List;

/**
 * @author yole
 */
public class IncomingChangesViewProvider implements ChangesViewContentProvider {
  private Project myProject;
  private MessageBus myBus;
  private CommittedChangesBrowser myBrowser;
  private MessageBusConnection myConnection;

  public IncomingChangesViewProvider(final Project project, final MessageBus bus) {
    myProject = project;
    myBus = bus;
  }

  public JComponent initContent() {
    final CommittedChangesCache cache = CommittedChangesCache.getInstance(myProject);
    final List<CommittedChangeList> list = cache.getIncomingChanges();
    final CommittedChangesProvider provider = cache.getProviderForProject();
    final CommittedChangesTableModel model = new CommittedChangesTableModel(list, provider.getColumns());
    myBrowser = new CommittedChangesBrowser(myProject, model);
    ActionGroup group = (ActionGroup) ActionManager.getInstance().getAction("IncomingChangesToolbar");
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    myBrowser.addToolBar(toolbar.getComponent());
    myConnection = myBus.connect();
    myConnection.subscribe(CommittedChangesCache.COMMITTED_TOPIC, new MyCommittedChangesListener());
    return myBrowser;
  }

  public void disposeContent() {
    myConnection.disconnect();
    myBrowser = null;
  }

  private void updateModel() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (myBrowser != null) {
          myBrowser.setItems(CommittedChangesCache.getInstance(myProject).getIncomingChanges());
        }
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
