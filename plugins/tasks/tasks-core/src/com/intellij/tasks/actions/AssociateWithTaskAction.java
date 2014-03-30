/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.tasks.actions;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.tasks.TaskManager;

/**
 * @author Dmitry Avdeev
 */
public class AssociateWithTaskAction extends ToggleAction implements DumbAware {

  @Override
  public void update(AnActionEvent e) {
    boolean isChangelist = e.getData(VcsDataKeys.CHANGE_LISTS) == null;
    e.getPresentation().setVisible(isChangelist);
    if (isChangelist) {
      super.update(e);   
    }
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    ChangeList[] lists = e.getData(VcsDataKeys.CHANGE_LISTS);
    if (lists == null) {
      return false;
    }
    Project project = e.getData(CommonDataKeys.PROJECT);
    TaskManager manager = TaskManager.getManager(project);
    for (ChangeList list : lists) {
      if (list instanceof LocalChangeList && manager.getAssociatedTask((LocalChangeList)list) == null) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    ChangeList[] lists = e.getData(VcsDataKeys.CHANGE_LISTS);
    if (lists == null) {
      return;
    }
    Project project = e.getData(CommonDataKeys.PROJECT);
    TaskManager manager = TaskManager.getManager(project);
    for (ChangeList list : lists) {
      if (list instanceof LocalChangeList) {
        manager.trackContext((LocalChangeList)list);
      }
    }
  }
}
