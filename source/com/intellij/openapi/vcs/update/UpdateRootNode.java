package com.intellij.openapi.vcs.update;

import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleTextAttributes;

import java.util.Iterator;
import java.util.List;

public class UpdateRootNode extends GroupTreeNode {

  private final Project myProject;

  public UpdateRootNode(UpdatedFiles updatedFiles, Project project, String rootName, ActionInfo actionInfo) {
    super(rootName, false, SimpleTextAttributes.ERROR_ATTRIBUTES, project);
    myProject = project;

    addGroupsToNode(updatedFiles.getTopLevelGroups(), this, actionInfo);
  }

  private void addGroupsToNode(List<FileGroup> groups, AbstractTreeNode owner, ActionInfo actionInfo) {
    for (Iterator<FileGroup> iterator = groups.iterator(); iterator.hasNext();) {
      FileGroup fileGroup = iterator.next();
      GroupTreeNode node = addFileGroup(fileGroup, owner, actionInfo);
      if (node != null) {
        addGroupsToNode(fileGroup.getChildren(), node, actionInfo);
      }
    }
  }

  private GroupTreeNode addFileGroup(FileGroup fileGroup, AbstractTreeNode parent, ActionInfo actionInfo) {
    if (fileGroup.isEmpty()) {
      return null;
    }
    GroupTreeNode group = new GroupTreeNode(actionInfo.getGroupName(fileGroup), fileGroup.getSupportsDeletion(),
                                            fileGroup.getInvalidAttributes(), myProject);
    parent.add(group);
    for (Iterator each = fileGroup.getFiles().iterator(); each.hasNext();) {
      group.addFilePath((String)each.next());
    }
    return group;
  }

  public boolean getSupportsDeletion() {
    return false;
  }
}
