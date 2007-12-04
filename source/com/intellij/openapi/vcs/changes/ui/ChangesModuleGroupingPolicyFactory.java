package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;

import javax.swing.tree.DefaultTreeModel;

/**
 * @author yole
 */
public class ChangesModuleGroupingPolicyFactory extends ChangesGroupingPolicyFactory {
  private Project myProject;

  public ChangesModuleGroupingPolicyFactory(final Project project) {
    myProject = project;
  }

  public ChangesGroupingPolicy createGroupingPolicy(final DefaultTreeModel model) {
    return new ChangesModuleGroupingPolicy(myProject, model);
  }
}