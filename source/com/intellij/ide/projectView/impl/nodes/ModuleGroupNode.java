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
import java.util.*;

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
    final Collection<ModuleGroup> childGroups = getValue().childGroups(getProject());
    final List<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    for (Iterator iterator = childGroups.iterator(); iterator.hasNext();) {
      ModuleGroup moduleGroup = (ModuleGroup)iterator.next();
      result.add(new ModuleGroupNode(getProject(), moduleGroup, getSettings(), myModuleNodeClass));
    }
    Module[] modules = getValue().modulesInGroup(getProject());
    final List<AbstractTreeNode> childModules = ProjectViewNode.wrap(Arrays.asList(modules), getProject(), myModuleNodeClass, getSettings());
    result.addAll(childModules);
    return result;
  }

  public boolean contains(VirtualFile file) {
    return someChildContainsFile(file);
  }

  public void update(PresentationData presentation) {
    final String[] groupPath = getValue().getGroupPath();
    presentation.setPresentableText(groupPath[groupPath.length-1]);
    presentation.setOpenIcon(OPEN_ICON);
    presentation.setClosedIcon(CLOSED_ICON);    
  }

  public String getTestPresentation() {
    return "Group: " + getValue().getGroupPath();
  }
}
