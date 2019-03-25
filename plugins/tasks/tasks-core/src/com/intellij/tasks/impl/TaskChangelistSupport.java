// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.tasks.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.ui.EditChangelistSupport;
import com.intellij.tasks.ChangeListInfo;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.actions.TaskAutoCompletionListProvider;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.util.Consumer;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public final class TaskChangelistSupport implements EditChangelistSupport {
  private final Project myProject;
  private final TaskManagerImpl myTaskManager;

  public TaskChangelistSupport(Project project) {
    myProject = project;
    myTaskManager = (TaskManagerImpl)TaskManager.getManager(project);
  }

  @Override
  public void installSearch(EditorTextField name, final EditorTextField comment) {
    Document document = name.getDocument();
    final TaskAutoCompletionListProvider completionProvider =
      new TaskAutoCompletionListProvider(myProject);

    TextFieldWithAutoCompletion.installCompletion(document, myProject, completionProvider, false);
  }

  @Override
  public Consumer<LocalChangeList> addControls(JPanel bottomPanel, final LocalChangeList initial) {
    final JCheckBox checkBox = new JCheckBox("Track context");
    checkBox.setMnemonic('t');
    checkBox.setToolTipText("Reload context (e.g. open editors) when changelist is set active");
    checkBox.setSelected(initial == null ?
                         myTaskManager.getState().trackContextForNewChangelist :
                         myTaskManager.getAssociatedTask(initial) != null);
    bottomPanel.add(checkBox);
    return changeList -> {
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
    };
  }

  @Override
  public void changelistCreated(LocalChangeList changeList) {
  }
}
