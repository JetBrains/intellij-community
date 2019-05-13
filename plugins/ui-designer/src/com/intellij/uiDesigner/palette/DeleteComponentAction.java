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

/**
 * @author yole
 */
public class DeleteComponentAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    ComponentItem selectedItem = e.getData(ComponentItem.DATA_KEY);
    GroupItem groupItem = e.getData(GroupItem.DATA_KEY);
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
    if (rc != Messages.YES) return;

    final Palette palette = Palette.getInstance(project);
    palette.removeItem(groupItem, selectedItem);
    palette.fireGroupsChanged();
  }

  @Override public void update(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    ComponentItem selectedItem = e.getData(ComponentItem.DATA_KEY);
    GroupItem groupItem = e.getData(GroupItem.DATA_KEY);
    e.getPresentation().setEnabled(project != null && selectedItem != null && groupItem != null &&
                                   !selectedItem.isAnyComponent() && selectedItem.isRemovable());
  }
}
