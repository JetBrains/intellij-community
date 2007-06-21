package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class IncomingChangesViewProvider implements ChangesViewContentProvider {
  private Project myProject;
  private MessageBus myBus;
  private CommittedChangesTreeBrowser myBrowser;
  private MessageBusConnection myConnection;
  private JLabel myErrorLabel = new JLabel();

  public IncomingChangesViewProvider(final Project project, final MessageBus bus) {
    myProject = project;
    myBus = bus;
  }

  public JComponent initContent() {
    myBrowser = new CommittedChangesTreeBrowser(myProject, Collections.<CommittedChangeList>emptyList());
    ActionGroup group = (ActionGroup) ActionManager.getInstance().getAction("IncomingChangesToolbar");
    final ActionToolbar toolbar = myBrowser.createGroupFilterToolbar(myProject, group);
    myBrowser.addToolBar(toolbar.getComponent());
    myBrowser.setTableContextMenu(group);
    myConnection = myBus.connect();
    myConnection.subscribe(CommittedChangesCache.COMMITTED_TOPIC, new MyCommittedChangesListener());
    loadChangesToBrowser();

    JPanel contentPane = new JPanel(new BorderLayout());
    contentPane.add(myBrowser, BorderLayout.CENTER);
    contentPane.add(myErrorLabel, BorderLayout.SOUTH);
    myErrorLabel.setForeground(Color.red);
    return contentPane;
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
    final CommittedChangesCache cache = CommittedChangesCache.getInstance(myProject);
    final List<CommittedChangeList> list = cache.getCachedIncomingChanges();
    if (list != null) {
      myBrowser.setItems(list, false);
    }
    else {
      cache.loadIncomingChangesAsync(null);
    }
  }

  private class MyCommittedChangesListener extends CommittedChangesAdapter {
    public void changesLoaded(final RepositoryLocation location, final List<CommittedChangeList> changes) {
      updateModel();
    }

    public void incomingChangesUpdated(final List<CommittedChangeList> receivedChanges) {
      updateModel();
    }

    public void refreshErrorStatusChanged(@Nullable final VcsException lastError) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (lastError != null) {
            myErrorLabel.setText("Error refreshing changes: " + lastError.getMessage());
          }
          else {
            myErrorLabel.setText("");
          }
        }
      });
    }
  }
}
