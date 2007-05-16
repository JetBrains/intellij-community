package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.Consumer;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;

import javax.swing.*;
import java.util.List;
import java.util.Collections;

/**
 * @author yole
 */
public class IncomingChangesViewProvider implements ChangesViewContentProvider {
  private Project myProject;
  private MessageBus myBus;
  private CommittedChangesTreeBrowser myBrowser;
  private MessageBusConnection myConnection;

  public IncomingChangesViewProvider(final Project project, final MessageBus bus) {
    myProject = project;
    myBus = bus;
  }

  public JComponent initContent() {
    final CommittedChangesCache cache = CommittedChangesCache.getInstance(myProject);
    final CommittedChangesProvider provider = cache.getProviderForProject();
    assert provider != null;
    myBrowser = new CommittedChangesTreeBrowser(myProject, Collections.<CommittedChangeList>emptyList());
    ActionGroup group = (ActionGroup) ActionManager.getInstance().getAction("IncomingChangesToolbar");
    final ActionToolbar toolbar = myBrowser.createGroupFilterToolbar(myProject, group);
    myBrowser.addToolBar(toolbar.getComponent());
    myBrowser.setTableContextMenu(group);
    myConnection = myBus.connect();
    myConnection.subscribe(CommittedChangesCache.COMMITTED_TOPIC, new MyCommittedChangesListener());
    loadChangesToBrowser();
    return myBrowser;
  }

  public void disposeContent() {
    myConnection.disconnect();
    myBrowser = null;
  }

  private void updateModel() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;
        if (myBrowser != null) {
          loadChangesToBrowser();
        }
      }
    });
  }

  private void loadChangesToBrowser() {
    CommittedChangesCache.getInstance(myProject).loadIncomingChangesAsync(new Consumer<List<CommittedChangeList>>() {
      public void consume(final List<CommittedChangeList> committedChangeLists) {
        myBrowser.setItems(committedChangeLists, false);
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
