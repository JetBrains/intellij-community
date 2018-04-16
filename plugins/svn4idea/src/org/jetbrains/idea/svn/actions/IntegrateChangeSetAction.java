// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.actions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.integrate.ChangeSetMergerFactory;
import org.jetbrains.idea.svn.integrate.MergerFactory;
import org.jetbrains.idea.svn.integrate.SelectedChangeSetChecker;
import org.jetbrains.idea.svn.integrate.SelectedCommittedStuffChecker;

public class IntegrateChangeSetAction extends AbstractIntegrateChangesAction<SelectedChangeSetChecker> {
  public IntegrateChangeSetAction() {
    super(true);
  }

  @NotNull
  protected MergerFactory createMergerFactory(SelectedChangeSetChecker checker) {
    return new ChangeSetMergerFactory(checker.getSelectedLists().get(0), checker.getSelectedChanges());
  }

  @NotNull
  protected SelectedChangeSetChecker createChecker() {
    return new SelectedChangeSetChecker();
  }

  @Nullable
  @Override
  protected Url getSelectedBranchUrl(SelectedCommittedStuffChecker checker) {
    return null;
  }

  protected String getSelectedBranchLocalPath(SelectedCommittedStuffChecker checker) {
    return null;
  }

  protected String getDialogTitle() {
    return null;
  }
}
