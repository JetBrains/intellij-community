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

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CommittedChangesViewManager implements ProjectComponent {
  private ProjectLevelVcsManager myVcsManager;
  private ChangesViewContentManager myContentManager;
  private CommittedChangesPanel myComponent;
  private Content myContent;
  private Project myProject;
  private CommittedChangesProvider myProvider = null;
  private VcsListener myVcsListener = new MyVcsListener();

  public CommittedChangesViewManager(final Project project, final ChangesViewContentManager contentManager,
                                     final ProjectLevelVcsManager vcsManager) {
    myProject = project;
    myContentManager = contentManager;
    myVcsManager = vcsManager;
  }

  public void projectOpened() {
    updateChangesContent();
    myVcsManager.addVcsListener(myVcsListener);
  }

  public void projectClosed() {
    myVcsManager.removeVcsListener(myVcsListener);
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
    List<AbstractVcs> vcsWithProviders = new ArrayList<AbstractVcs>();
    for(AbstractVcs vcs: vcss) {
      if (vcs.getCommittedChangesProvider() != null) {
        vcsWithProviders.add(vcs);
      }
    }
    CommittedChangesProvider oldProvider = myProvider;
    if (vcsWithProviders.size() == 0) {
      myProvider = null;
    }
    else if (vcsWithProviders.size() == 1) {
      myProvider = vcsWithProviders.get(0).getCommittedChangesProvider();
    }
    else {
      myProvider = new CompositeCommittedChangesProvider(vcsWithProviders.toArray(new AbstractVcs[vcsWithProviders.size()]));
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
          myComponent = new CommittedChangesPanel(myProject, myProvider, myProvider.createDefaultSettings());
        }
        else {
          myComponent.setProvider(myProvider);
        }
        myContent = PeerFactory.getInstance().getContentFactory().createContent(myComponent, "Committed", false);
        myContent.setCloseable(false);
        myContentManager.addContent(myContent);
      }
    }
  }

  private class MyVcsListener implements VcsListener {
    public void directoryMappingChanged() {
      updateChangesContent();
    }
  }
}