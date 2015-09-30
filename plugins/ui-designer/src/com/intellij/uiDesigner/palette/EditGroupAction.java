/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.uiDesigner.palette;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.uiDesigner.UIDesignerBundle;

import java.util.List;

/**
 * @author yole
 */
public class EditGroupAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    GroupItem groupToBeEdited = GroupItem.DATA_KEY.getData(e.getDataContext());
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
  public void update(AnActionEvent e) {
    Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    GroupItem groupItem = GroupItem.DATA_KEY.getData(e.getDataContext());
    e.getPresentation().setEnabled(project != null && groupItem != null && !groupItem.isReadOnly());
  }
}
