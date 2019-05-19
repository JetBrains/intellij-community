// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.palette;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.uiDesigner.UIDesignerBundle;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author yole
 */
public class EditComponentAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.palette.EditComponentAction");

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    ComponentItem selectedItem = e.getData(ComponentItem.DATA_KEY);
    if (project == null || selectedItem == null || selectedItem.isAnyComponent() || selectedItem.isSpacer()) {
      return;
    }

    final ComponentItem itemToBeEdited = selectedItem.clone(); /*"Cancel" should work, so we need edit copy*/
    Window parentWindow = WindowManager.getInstance().suggestParentWindow(project);
    final ComponentItemDialog dialog = new ComponentItemDialog(project, parentWindow, itemToBeEdited, false);
    dialog.setTitle(UIDesignerBundle.message("title.edit.component"));
    if (!dialog.showAndGet()) {
      return;
    }

    GroupItem groupItem = null;
    Palette palette = Palette.getInstance(project);
    // If the itemToBeAdded is already in palette do nothing
    for (GroupItem group : palette.getGroups()) {
      if (group.containsItemCopy(selectedItem, itemToBeEdited.getClassName())) {
        return;
      }
      if (group.containsItemClass(selectedItem.getClassName())) {
        groupItem = group;
      }
    }
    LOG.assertTrue(groupItem != null);

    palette.replaceItem(groupItem, selectedItem, itemToBeEdited);
    palette.fireGroupsChanged();
  }

  @Override public void update(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    ComponentItem selectedItem = e.getData(ComponentItem.DATA_KEY);
    GroupItem groupItem = e.getData(GroupItem.DATA_KEY);
    e.getPresentation().setEnabled(project != null &&
                                   selectedItem != null &&
                                   groupItem != null &&
                                   !selectedItem.isAnyComponent() &&
                                   !selectedItem.isSpacer());
  }
}
