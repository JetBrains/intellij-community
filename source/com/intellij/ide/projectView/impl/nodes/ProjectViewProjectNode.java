package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class ProjectViewProjectNode extends AbstractProjectNode {

  public ProjectViewProjectNode(Project project, ViewSettings viewSettings) {
    super(project, project, viewSettings);
  }

  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    final Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    return modulesAndGroups(modules);
  }

  protected Class<? extends AbstractTreeNode> getModuleNodeClass() {
    return ProjectViewModuleNode.class;
  }

}
