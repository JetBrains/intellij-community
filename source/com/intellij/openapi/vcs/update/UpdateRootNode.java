package com.intellij.openapi.vcs.update;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class UpdateRootNode extends GroupTreeNode {

  private final Project myProject;

  public UpdateRootNode(UpdatedFiles updatedFiles, Project project, String rootName, ActionInfo actionInfo) {
    super(rootName, false, SimpleTextAttributes.ERROR_ATTRIBUTES, project);
    myProject = project;

    addGroupsToNode(updatedFiles.getTopLevelGroups(), this, actionInfo);
  }

  private void addGroupsToNode(List<FileGroup> groups, AbstractTreeNode owner, ActionInfo actionInfo) {
    for (FileGroup fileGroup : groups) {
      GroupTreeNode node = addFileGroup(fileGroup, owner, actionInfo);
      if (node != null) {
        addGroupsToNode(fileGroup.getChildren(), node, actionInfo);
      }
    }
  }

  @Nullable
  private GroupTreeNode addFileGroup(FileGroup fileGroup, AbstractTreeNode parent, ActionInfo actionInfo) {
    if (fileGroup.isEmpty()) {
      return null;
    }
    GroupTreeNode group = new GroupTreeNode(actionInfo.getGroupName(fileGroup), fileGroup.getSupportsDeletion(),
                                            fileGroup.getInvalidAttributes(), myProject);
    Disposer.register(this, group);
    parent.add(group);
    for (final String s : fileGroup.getFiles()) {
      group.addFilePath(s);
    }
    return group;
  }

  public boolean getSupportsDeletion() {
    return false;
  }
}
