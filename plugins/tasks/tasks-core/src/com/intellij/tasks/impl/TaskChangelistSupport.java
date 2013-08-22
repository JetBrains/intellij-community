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

package com.intellij.tasks.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.ui.EditChangelistSupport;
import com.intellij.tasks.ChangeListInfo;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.actions.TaskAutoCompletionListProvider;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.TextFieldWithAutoCompletionContributor;
import com.intellij.util.Consumer;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class TaskChangelistSupport implements EditChangelistSupport {

  private final Project myProject;
  private final TaskManagerImpl myTaskManager;

  public TaskChangelistSupport(Project project, TaskManagerImpl taskManager) {
    myProject = project;
    myTaskManager = taskManager;
  }

  public void installSearch(EditorTextField name, final EditorTextField comment) {
    Document document = name.getDocument();
    final TaskAutoCompletionListProvider completionProvider =
      new TaskAutoCompletionListProvider(myProject);

    TextFieldWithAutoCompletionContributor.installCompletion(document, myProject, completionProvider, false);
  }

  public Consumer<LocalChangeList> addControls(JPanel bottomPanel, final LocalChangeList initial) {
    final JCheckBox checkBox = new JCheckBox("Track context");
    checkBox.setMnemonic('t');
    checkBox.setToolTipText("Reload context (e.g. open editors) when changelist is set active");
    checkBox.setSelected(initial == null ?
                         myTaskManager.getState().trackContextForNewChangelist :
                         myTaskManager.getAssociatedTask(initial) != null);
    bottomPanel.add(checkBox);
    return new Consumer<LocalChangeList>() {
      public void consume(LocalChangeList changeList) {
        if (initial == null) {
          myTaskManager.getState().trackContextForNewChangelist = checkBox.isSelected();
          if (checkBox.isSelected()) {
            myTaskManager.trackContext(changeList);
          }
          else {
            myTaskManager.getActiveTask().addChangelist(new ChangeListInfo(changeList));
          }
        }
        else {
          final LocalTask associatedTask = myTaskManager.getAssociatedTask(changeList);
          if (checkBox.isSelected()) {
            if (associatedTask == null) {
              myTaskManager.trackContext(changeList);
            }
          }
          else {
            if (associatedTask != null) {
              myTaskManager.removeTask(associatedTask);
            }
          }
        }
      }
    };
  }

  public void changelistCreated(LocalChangeList changeList) {
  }
}
