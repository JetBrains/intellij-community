package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class ModuleGroupNode extends ProjectViewNode<ModuleGroup> {
  public ModuleGroupNode(final Project project, final ModuleGroup value, final ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }
   public ModuleGroupNode(final Project project, final Object value, final ViewSettings viewSettings) {
    this(project, (ModuleGroup)value, viewSettings);
  }

  protected abstract Class<? extends AbstractTreeNode> getModuleNodeClass();
  protected abstract ModuleGroupNode createModuleGroupNode(ModuleGroup moduleGroup);

  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    final Collection<ModuleGroup> childGroups = getValue().childGroups(getProject());
    final List<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    for (final ModuleGroup childGroup : childGroups) {
      result.add(createModuleGroupNode(childGroup));
    }
    Collection<Module> modules = getValue().modulesInGroup(getProject(), false);
    final List<AbstractTreeNode> childModules = ProjectViewNode.wrap(modules, getProject(), getModuleNodeClass(), getSettings());
    result.addAll(childModules);
    return result;
  }

  public boolean contains(@NotNull VirtualFile file) {
    return someChildContainsFile(file);
  }

  public void update(PresentationData presentation) {
    final String[] groupPath = getValue().getGroupPath();
    presentation.setPresentableText(groupPath[groupPath.length-1]);
    presentation.setOpenIcon(Icons.OPENED_MODULE_GROUP_ICON);
    presentation.setClosedIcon(Icons.CLOSED_MODULE_GROUP_ICON);
  }

  public String getTestPresentation() {
    return "Group: " + getValue();
  }

  public String getToolTip() {
    return IdeBundle.message("tooltip.module.group");
  }
  }
