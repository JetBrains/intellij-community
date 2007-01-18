/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 23.11.2006
 * Time: 13:31:30
 */
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.vcs.actions.AbstractCommonCheckinAction;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ActionPlaces;
import org.jetbrains.annotations.Nullable;

public class ShelveChangesAction extends AbstractCommonCheckinAction {
  protected String getActionName(VcsContext dataContext) {
    return VcsBundle.message("shelve.changes.action");
  }

  protected FilePath[] getRoots(VcsContext context) {
    return getAllContentRoots(context);
  }

  protected boolean filterRootsBeforeAction() {
    return false;
  }

  @Override @Nullable
  protected CommitExecutor getExecutor(Project project) {
    return new ShelveChangesCommitExecutor(project);
  }

  @Override
  protected void update(final VcsContext vcsContext, final Presentation presentation) {
    super.update(vcsContext, presentation);
    if (presentation.isVisible() && presentation.isEnabled() && vcsContext.getPlace().equals(ActionPlaces.CHANGES_VIEW_POPUP)) {
      final ChangeList[] selectedChangeLists = vcsContext.getSelectedChangeLists();
      final Change[] selectedChanges = vcsContext.getSelectedChanges();
      if (selectedChangeLists != null) {
        presentation.setEnabled(selectedChangeLists.length == 1);
      }
      else {
        presentation.setEnabled (selectedChanges != null && selectedChanges.length > 0);
      }
    }
  }
}