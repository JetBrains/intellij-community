package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.project.Project;

import javax.swing.*;

/**
 * @author yole
 */
public class SelectFilteringAction extends LabeledComboBoxAction {
  private final Project myProject;
  private CommittedChangesTreeBrowser myBrowser;

  public SelectFilteringAction(final Project project, final CommittedChangesTreeBrowser browser) {
    super("Filter by");
    myProject = project;
    myBrowser = browser;
  }

  protected ComboBoxModel createModel() {
    return new DefaultComboBoxModel(new Object[] {
      ChangeListFilteringStrategy.NONE,
      new UserFilteringStrategy(),
      new StructureFilteringStrategy(myProject)
    });
  }

  protected void selectionChanged(final Object selection) {
    myBrowser.setFilteringStrategy((ChangeListFilteringStrategy)selection);
  }
}