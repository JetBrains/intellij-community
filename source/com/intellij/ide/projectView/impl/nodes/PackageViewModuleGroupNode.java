package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;

/**
 * User: anna
 * Date: Feb 22, 2005
 */
public class PackageViewModuleGroupNode extends ModuleGroupNode {
  public PackageViewModuleGroupNode(final Project project, final Object value, final ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  public PackageViewModuleGroupNode(final Project project, final ModuleGroup value, final ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  protected Class<? extends AbstractTreeNode> getModuleNodeClass() {
    return PackageViewModuleNode.class;
  }

  protected ModuleGroupNode createModuleGroupNode(ModuleGroup moduleGroup) {
    return new PackageViewModuleGroupNode(getProject(), moduleGroup, getSettings());
  }
}
