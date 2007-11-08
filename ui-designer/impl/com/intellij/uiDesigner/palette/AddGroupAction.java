/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.palette;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.uiDesigner.UIDesignerBundle;

import java.util.ArrayList;

/**
 * @author yole
 */
public class AddGroupAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return;
    // Ask group name
    final String groupName = Messages.showInputDialog(
      project,
      UIDesignerBundle.message("message.enter.group.name"),
      UIDesignerBundle.message("title.add.group"),
      Messages.getQuestionIcon()
    );
    if(groupName == null){
      return;
    }

    Palette palette = Palette.getInstance(project);
    // Check that name of the group is unique
    final ArrayList<GroupItem> groups = palette.getGroups();
    for(int i = groups.size() - 1; i >= 0; i--){
      if(groupName.equals(groups.get(i).getName())){
        Messages.showErrorDialog(project,
                                 UIDesignerBundle.message("error.group.name.unique"),
                                 CommonBundle.getErrorTitle());
        return;
      }
    }

    final GroupItem groupToBeAdded = new GroupItem(groupName);
    ArrayList<GroupItem> newGroups = new ArrayList<GroupItem>(groups);
    newGroups.add(groupToBeAdded);
    palette.setGroups(newGroups);
  }
}
