/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.palette;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.uiDesigner.UIDesignerBundle;

import java.awt.*;

/**
 * @author yole
 */
public class EditComponentAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    ComponentItem selectedItem = (ComponentItem) e.getDataContext().getData(ComponentItem.class.getName());
    GroupItem groupItem = (GroupItem) e.getDataContext().getData(GroupItem.class.getName());
    if (project == null || selectedItem == null || groupItem == null) return;

    final ComponentItem itemToBeEdited = selectedItem.clone(); /*"Cancel" should work, so we need edit copy*/
    Window parentWindow = WindowManager.getInstance().suggestParentWindow(project);
    final ComponentItemDialog dialog = new ComponentItemDialog(project, parentWindow, itemToBeEdited);
    dialog.setTitle(UIDesignerBundle.message("title.edit.component"));
    dialog.show();
    if(!dialog.isOK()) {
      return;
    }

    Palette palette = Palette.getInstance(project);
    // If the itemToBeAdded is already in palette do nothing
    for(GroupItem group: palette.getGroups()) {
      if (group.containsItemCopy(selectedItem, itemToBeEdited.getClassName())){
        return;
      }
    }

    groupItem.replaceItem(selectedItem, itemToBeEdited);
    palette.fireGroupsChanged();
  }
}
