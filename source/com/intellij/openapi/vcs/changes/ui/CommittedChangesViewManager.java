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
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class CommittedChangesViewManager implements ProjectComponent {
  private ProjectLevelVcsManager myVcsManager;
  private ChangesViewContentManager myContentManager;
  private CommittedChangesPanel myComponent;
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
          myComponent = new CommittedChangesPanel(myProject, myProvider, myProvider.createDefaultSettings());
        }
        myContent = PeerFactory.getInstance().getContentFactory().createContent(myComponent, "Committed", false);
        myContent.setCloseable(false);
        myContentManager.addContent(myContent);
      }
    }
  }
}