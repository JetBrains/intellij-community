// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.palette;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.uiDesigner.UIDesignerBundle;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public class EditGroupAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    GroupItem groupToBeEdited = e.getData(GroupItem.DATA_KEY);
    if (groupToBeEdited == null || project == null) return;

    // Ask group name
    final String groupName = Messages.showInputDialog(
      project,
      UIDesignerBundle.message("edit.enter.group.name"),
      UIDesignerBundle.message("title.edit.group"),
      Messages.getQuestionIcon(),
      groupToBeEdited.getName(),
      null
    );
    if (groupName == null || groupName.equals(groupToBeEdited.getName())) {
      return;
    }

    Palette palette = Palette.getInstance(project);
    List<GroupItem> groups = palette.getGroups();
    for (int i = groups.size() - 1; i >= 0; i--) {
      if (groupName.equals(groups.get(i).getName())) {
        Messages.showErrorDialog(project, UIDesignerBundle.message("error.group.name.unique"),
                                 CommonBundle.getErrorTitle());
        return;
      }
    }

    groupToBeEdited.setName(groupName);
    palette.fireGroupsChanged();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    GroupItem groupItem = e.getData(GroupItem.DATA_KEY);
    e.getPresentation().setEnabled(project != null && groupItem != null && !groupItem.isReadOnly());
  }
}
