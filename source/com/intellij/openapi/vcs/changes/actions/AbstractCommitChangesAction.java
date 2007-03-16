/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.vcs.actions.AbstractCommonCheckinAction;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ActionPlaces;

/**
 * @author yole
 */
public abstract class AbstractCommitChangesAction extends AbstractCommonCheckinAction {
  protected FilePath[] getRoots(VcsContext context) {
    return getAllContentRoots(context);
  }

  protected boolean filterRootsBeforeAction() {
    return false;
  }

  @Override
  protected void update(final VcsContext vcsContext, final Presentation presentation) {
    super.update(vcsContext, presentation);
    if (presentation.isVisible() && presentation.isEnabled() && vcsContext.getPlace().equals(ActionPlaces.CHANGES_VIEW_POPUP)) {
      final ChangeList[] selectedChangeLists = vcsContext.getSelectedChangeLists();
      final Change[] selectedChanges = vcsContext.getSelectedChanges();
      if (selectedChangeLists != null && selectedChangeLists.length > 0) {
        presentation.setEnabled(selectedChangeLists.length == 1);
      }
      else {
        presentation.setEnabled (selectedChanges != null && selectedChanges.length > 0);
      }
    }
  }
}