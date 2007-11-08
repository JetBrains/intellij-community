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
public class DeleteGroupAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    GroupItem groupToBeRemoved = e.getData(GroupItem.DATA_KEY);
    if (groupToBeRemoved == null || project == null) return;

    if(!Palette.isRemovable(groupToBeRemoved)){
      Messages.showInfoMessage(
        project,
        UIDesignerBundle.message("error.cannot.remove.default.group"),
        CommonBundle.getErrorTitle()
      );
      return;
    }

    Palette palette = Palette.getInstance(project);
    ArrayList<GroupItem> groups = new ArrayList<GroupItem>(palette.getGroups());
    groups.remove(groupToBeRemoved);
    palette.setGroups(groups);
  }

  @Override public void update(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    GroupItem groupItem = e.getData(GroupItem.DATA_KEY);
    ComponentItem selectedItem = e.getData(ComponentItem.DATA_KEY);
    e.getPresentation().setEnabled(project != null && groupItem != null && !groupItem.isReadOnly() && selectedItem == null);
  }
}
