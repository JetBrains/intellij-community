/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.palette;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.UIDesignerBundle;

import java.util.HashMap;
import java.awt.*;

/**
 * @author yole
 */
public class AddComponentAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    GroupItem groupItem = (GroupItem)e.getDataContext().getData(GroupItem.class.getName());
    if (project == null || groupItem == null) return;

    // Show dialog
    final ComponentItem itemToBeAdded = new ComponentItem(
      project,
      "",
      null,
      null,
      new GridConstraints(),
      new HashMap<String, StringDescriptor>(),
      true/*all user defined components are removable*/,
      false,
      false
    );
    Window parentWindow = WindowManager.getInstance().suggestParentWindow(project);
    final ComponentItemDialog dialog = new ComponentItemDialog(project, parentWindow, itemToBeAdded);
    dialog.setTitle(UIDesignerBundle.message("title.add.component"));
    dialog.show();
    if(!dialog.isOK()){
      return;
    }

    // If the itemToBeAdded is already in palette do nothing
    if(groupItem.containsItemClass(itemToBeAdded.getClassName())){
      return;
    }

    // add to the group

    final Palette palette = Palette.getInstance(project);
    palette.addItem(groupItem, itemToBeAdded);
    palette.fireGroupsChanged();
  }
}
