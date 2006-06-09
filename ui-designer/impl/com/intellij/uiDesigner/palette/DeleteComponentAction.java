/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.palette;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.uiDesigner.UIDesignerBundle;

/**
 * @author yole
 */
public class DeleteComponentAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    ComponentItem selectedItem = (ComponentItem) e.getDataContext().getData(ComponentItem.class.getName());
    GroupItem groupItem = (GroupItem) e.getDataContext().getData(GroupItem.class.getName());
    if (project == null || selectedItem == null || groupItem == null) return;

    if(!selectedItem.isRemovable()){
      Messages.showInfoMessage(
        project,
        UIDesignerBundle.message("error.cannot.remove.default.palette"),
        CommonBundle.getErrorTitle()
      );
      return;
    }

    int rc = Messages.showYesNoDialog(project, UIDesignerBundle.message("delete.component.prompt", selectedItem.getClassShortName()),
                                      UIDesignerBundle.message("delete.component.title"), Messages.getQuestionIcon());
    if (rc != 0) return;

    final Palette palette = Palette.getInstance(project);
    palette.removeItem(groupItem, selectedItem);
    palette.fireGroupsChanged();
  }

  @Override public void update(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    ComponentItem selectedItem = (ComponentItem) e.getDataContext().getData(ComponentItem.class.getName());
    GroupItem groupItem = (GroupItem) e.getDataContext().getData(GroupItem.class.getName());
    e.getPresentation().setEnabled(project != null && selectedItem != null && groupItem != null &&
                                   !selectedItem.isAnyComponent() && selectedItem.isRemovable());
  }
}
