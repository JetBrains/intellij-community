package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

import javax.swing.tree.DefaultTreeModel;

/**
 * @author yole
 */
public abstract class ChangesGroupingPolicyFactory {
  public static ChangesGroupingPolicyFactory getInstance(Project project) {
    return ServiceManager.getService(project, ChangesGroupingPolicyFactory.class);
  }

  public abstract ChangesGroupingPolicy createGroupingPolicy(final DefaultTreeModel model);
}