package org.jetbrains.idea.svn.actions;

import org.jetbrains.idea.svn.integrate.SelectedChangeSetChecker;

public class IntegrateChangeSetAction extends AbstractIntegrateChangesAction {
  public IntegrateChangeSetAction() {
    super(new SelectedChangeSetChecker(), true);
  }
}
