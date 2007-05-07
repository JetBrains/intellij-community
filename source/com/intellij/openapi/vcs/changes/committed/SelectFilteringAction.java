package com.intellij.openapi.vcs.changes.committed;

import javax.swing.*;

/**
 * @author yole
 */
public class SelectFilteringAction extends LabeledComboBoxAction {
  private CommittedChangesTreeBrowser myBrowser;

  public SelectFilteringAction(final CommittedChangesTreeBrowser browser) {
    super("Filter by");
    myBrowser = browser;
  }

  protected ComboBoxModel createModel() {
    return new DefaultComboBoxModel(new Object[] { ChangeListFilteringStrategy.NONE, new UserFilteringStrategy()});
  }

  protected void selectionChanged(final Object selection) {
    myBrowser.setFilteringStrategy((ChangeListFilteringStrategy)selection);
  }
}