// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.palette;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.uiDesigner.UIDesignerBundle;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;


public class AddGroupAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    // Ask group name
    final String groupName = Messages.showInputDialog(
      project,
      UIDesignerBundle.message("message.enter.group.name"),
      UIDesignerBundle.message("title.add.group"),
      Messages.getQuestionIcon()
    );
    if (groupName == null) {
      return;
    }

    Palette palette = Palette.getInstance(project);
    // Check that name of the group is unique
    List<GroupItem> groups = palette.getGroups();
    for (int i = groups.size() - 1; i >= 0; i--) {
      if (groupName.equals(groups.get(i).getName())) {
        Messages.showErrorDialog(project,
                                 UIDesignerBundle.message("error.group.name.unique"),
                                 CommonBundle.getErrorTitle());
        return;
      }
    }

    final GroupItem groupToBeAdded = new GroupItem(groupName);
    ArrayList<GroupItem> newGroups = new ArrayList<>(groups);
    newGroups.add(groupToBeAdded);
    palette.setGroups(newGroups);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
