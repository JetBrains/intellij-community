package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public class ProjectViewProjectNode extends AbstractProjectNode {

  public ProjectViewProjectNode(Project project, ViewSettings viewSettings) {
    super(project, project, viewSettings);
  }

  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    final Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    final ArrayList<AbstractTreeNode> nodes = new ArrayList<AbstractTreeNode>();
    nodes.addAll(modulesAndGroups(modules));
    final VirtualFile baseDir = getProject().getBaseDir();
    final PsiManager psiManager = PsiManager.getInstance(getProject());
    final VirtualFile[] files = baseDir.getChildren();
    for (VirtualFile file : files) {
      if (ModuleUtil.findModuleForFile(file, getProject()) == null) {
        if (!file.isDirectory()) {
          nodes.add(new PsiFileNode(getProject(), psiManager.findFile(file), getSettings()));
        }
      }
    }
    return nodes;
  }

  protected Class<? extends AbstractTreeNode> getModuleNodeClass() {
    return ProjectViewModuleNode.class;
  }

}
