/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

  @Override public void update(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    GroupItem groupItem = e.getData(GroupItem.DATA_KEY);
    ComponentItem selectedItem = e.getData(ComponentItem.DATA_KEY);
    e.getPresentation().setEnabled(project != null && groupItem != null && !groupItem.isReadOnly() && selectedItem == null);
  }
}
