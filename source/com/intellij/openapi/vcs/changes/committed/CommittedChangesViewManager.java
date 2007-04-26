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
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;

import javax.swing.*;

public class CommittedChangesViewManager implements ChangesViewContentProvider {
  public static CommittedChangesViewManager getInstance(Project project) {
    return project.getComponent(CommittedChangesViewManager.class);
  }

  private ProjectLevelVcsManager myVcsManager;
  private CommittedChangesPanel myComponent;
  private Project myProject;
  private VcsListener myVcsListener = new MyVcsListener();

  public CommittedChangesViewManager(final Project project, final ProjectLevelVcsManager vcsManager) {
    myProject = project;
    myVcsManager = vcsManager;
  }

  private void updateChangesContent() {
    final CommittedChangesProvider provider = CommittedChangesCache.getInstance(myProject).getProviderForProject();

    if (myComponent == null) {
      myComponent = new CommittedChangesPanel(myProject, provider, provider.createDefaultSettings());
      myComponent.setMaxCount(50);
    }
    else {
      myComponent.setProvider(provider);
    }
  }

  public JComponent initContent() {
    myVcsManager.addVcsListener(myVcsListener);
    updateChangesContent();
    myComponent.refreshChanges(true);
    return myComponent;
  }

  public void disposeContent() {
    myVcsManager.removeVcsListener(myVcsListener);
    myComponent = null;
  }

  private class MyVcsListener implements VcsListener {
    public void directoryMappingChanged() {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          updateChangesContent();
        }
      });
    }
  }
}