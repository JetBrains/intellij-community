// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @Override
  protected @NotNull MergerFactory createMergerFactory(SelectedChangeSetChecker checker) {
    return new ChangeSetMergerFactory(checker.getSelectedLists().get(0), checker.getSelectedChanges());
  }

  @Override
  protected @NotNull SelectedChangeSetChecker createChecker() {
    return new SelectedChangeSetChecker();
  }

  @Override
  protected @Nullable Url getSelectedBranchUrl(SelectedCommittedStuffChecker checker) {
    return null;
  }

  @Override
  protected String getSelectedBranchLocalPath(SelectedCommittedStuffChecker checker) {
    return null;
  }

  @Override
  protected String getDialogTitle() {
    return null;
  }
}
