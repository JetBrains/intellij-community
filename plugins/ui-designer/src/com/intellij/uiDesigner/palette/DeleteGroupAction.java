// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.palette;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.uiDesigner.UIDesignerBundle;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author yole
 */
public class DeleteGroupAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
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
    ArrayList<GroupItem> groups = new ArrayList<>(palette.getGroups());
    groups.remove(groupToBeRemoved);
    palette.setGroups(groups);
  }

  @Override public void update(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    GroupItem groupItem = e.getData(GroupItem.DATA_KEY);
    ComponentItem selectedItem = e.getData(ComponentItem.DATA_KEY);
    e.getPresentation().setEnabled(project != null && groupItem != null && !groupItem.isReadOnly() && selectedItem == null);
  }
}
