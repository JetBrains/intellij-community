package com.intellij.openapi.vcs.changes.committed;

import javax.swing.*;

/**
 * @author yole
 */
public class SelectGroupingAction extends LabeledComboBoxAction {
  private CommittedChangesTreeBrowser myBrowser;

  public SelectGroupingAction(final CommittedChangesTreeBrowser browser) {
    super("Group by");
    myBrowser = browser;
  }

  protected void selectionChanged(Object selection) {
    myBrowser.setGroupingStrategy((ChangeListGroupingStrategy) selection);
  }

  protected ComboBoxModel createModel() {
    return new DefaultComboBoxModel(new Object[] { ChangeListGroupingStrategy.DATE, ChangeListGroupingStrategy.USER });
  }
}