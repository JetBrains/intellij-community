// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.tasks.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.ui.EditChangelistSupport;
import com.intellij.tasks.ChangeListInfo;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.actions.TaskAutoCompletionListProvider;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class TaskChangelistSupport implements EditChangelistSupport {
  private final Project myProject;
  private final TaskManagerImpl myTaskManager;

  public TaskChangelistSupport(Project project) {
    myProject = project;
    myTaskManager = (TaskManagerImpl)TaskManager.getManager(project);
  }

  @Override
  public void installSearch(@NotNull EditorTextField name, @NotNull EditorTextField comment) {
    Document document = name.getDocument();
    final TaskAutoCompletionListProvider completionProvider =
      new TaskAutoCompletionListProvider(myProject);

    TextFieldWithAutoCompletion.installCompletion(document, myProject, completionProvider, false);
  }

  @Override
  public @NotNull Consumer<@NotNull LocalChangeList> addControls(@NotNull JPanel bottomPanel, @Nullable LocalChangeList initial) {
    final JCheckBox checkBox = new JCheckBox(TaskBundle.message("switch.changelist.track.context.checkbox"));
    checkBox.setToolTipText(TaskBundle.message("switch.changelist.track.context.checkbox.tooltip"));
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
}
