package org.jetbrains.idea.svn.actions;

import org.jetbrains.annotations.NotNull;
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

  protected String getSelectedBranchUrl(SelectedCommittedStuffChecker checker) {
    return null;
  }

  protected String getSelectedBranchLocalPath(SelectedCommittedStuffChecker checker) {
    return null;
  }

  protected String getDialogTitle() {
    return null;
  }
}
