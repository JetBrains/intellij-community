package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;

public class ModuleGroupNode extends ProjectViewNode<ModuleGroup> {

  private static final Icon OPEN_ICON = IconLoader.getIcon("/actions/newFolder.png");
  private static final Icon CLOSED_ICON = OPEN_ICON;
  private final Class<? extends AbstractTreeNode> myModuleNodeClass;

  public ModuleGroupNode(final Project project,
                 final ModuleGroup value,
                 final ViewSettings viewSettings,
                 final Class<? extends AbstractTreeNode> moduleNodeClass) {
    super(project, value, viewSettings);
    myModuleNodeClass = moduleNodeClass;
  }

  public Collection<AbstractTreeNode> getChildren() {
    Module[] modules = getValue().modulesInGroup(getProject());
    return ProjectViewNode.wrap(Arrays.asList(modules), getProject(), myModuleNodeClass, getSettings());
  }

  public boolean hasDescription() {
    return true;
  }

  public String getDescription() {
    return "Module Group";
  }

  public boolean contains(VirtualFile file) {
    return someChildContainsFile(file);
  }

  public void update(PresentationData presentation) {
    presentation.setPresentableText(getValue().getName());
    presentation.setOpenIcon(OPEN_ICON);
    presentation.setClosedIcon(CLOSED_ICON);    
  }

  public String getTestPresentation() {
    return "Group: " + getValue().getName();
  }
}
