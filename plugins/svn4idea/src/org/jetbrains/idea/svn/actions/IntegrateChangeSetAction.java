package org.jetbrains.idea.svn.actions;

import org.jetbrains.idea.svn.integrate.SelectedChangeSetChecker;

public class IntegrateChangeSetAction extends AbstractIntegrateChangesAction {
  public IntegrateChangeSetAction() {
    super(new SelectedChangeSetChecker(), true);
  }

  protected String getSelectedBranchUrl() {
    return null;
  }

  protected String getSelectedBranchLocalPath() {
    return null;
  }

  protected String getDialogTitle() {
    return null;
  }
}
