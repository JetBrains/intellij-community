/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 30.11.2006
 * Time: 18:12:47
 */
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;

import javax.swing.*;
import java.util.List;

public class CommittedChangesViewManager implements ChangesViewContentProvider {
  private ProjectLevelVcsManager myVcsManager;
  private MessageBus myBus;
  private MessageBusConnection myConnection;
  private CommittedChangesPanel myComponent;
  private Project myProject;
  private VcsListener myVcsListener = new MyVcsListener();

  public CommittedChangesViewManager(final Project project, final ProjectLevelVcsManager vcsManager, final MessageBus bus) {
    myProject = project;
    myVcsManager = vcsManager;
    myBus = bus;
  }

  private void updateChangesContent() {
    final CommittedChangesProvider provider = CommittedChangesCache.getInstance(myProject).getProviderForProject();
    if (provider == null) return;

    if (myComponent == null) {
      myComponent = new CommittedChangesPanel(myProject, provider, provider.createDefaultSettings());
    }
    else {
      myComponent.setProvider(provider);
    }
  }

  public JComponent initContent() {
    myVcsManager.addVcsListener(myVcsListener);
    myConnection = myBus.connect();
    myConnection.subscribe(CommittedChangesCache.COMMITTED_TOPIC, new MyCommittedChangesListener());
    updateChangesContent();
    myComponent.refreshChanges(true);
    return myComponent;
  }

  public void disposeContent() {
    myVcsManager.removeVcsListener(myVcsListener);
    myConnection.disconnect();
    myComponent = null;
  }

  private class MyVcsListener implements VcsListener {
    public void directoryMappingChanged() {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (!myProject.isDisposed()) {
            updateChangesContent();
          }
        }
      });
    }
  }

  private class MyCommittedChangesListener implements CommittedChangesListener {
    public void changesLoaded(RepositoryLocation location, List<CommittedChangeList> changes) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (myComponent != null && !myProject.isDisposed()) {
            myComponent.refreshChanges(true);
          }
        }
      });
    }

    public void incomingChangesUpdated(final List<CommittedChangeList> receivedChanges) {
    }
  }
}