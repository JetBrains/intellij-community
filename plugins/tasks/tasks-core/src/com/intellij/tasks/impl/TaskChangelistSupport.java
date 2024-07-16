// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;

@VisibleForTesting
public final class TaskChangelistSupport implements EditChangelistSupport {
  private final Project project;

  public TaskChangelistSupport(Project project) {
    this.project = project;
  }

  @Override
  public void installSearch(@NotNull EditorTextField name, @NotNull EditorTextField comment) {
    Document document = name.getDocument();
    TaskAutoCompletionListProvider completionProvider = new TaskAutoCompletionListProvider(project);
    TextFieldWithAutoCompletion.installCompletion(document, project, completionProvider, false);
  }

  @Override
  public @NotNull Consumer<@NotNull LocalChangeList> addControls(@NotNull JPanel bottomPanel, @Nullable LocalChangeList initial) {
    TaskManagerImpl taskManager = (TaskManagerImpl)TaskManager.getManager(project);

    JCheckBox checkBox = new JCheckBox(TaskBundle.message("switch.changelist.track.context.checkbox"));
    checkBox.setToolTipText(TaskBundle.message("switch.changelist.track.context.checkbox.tooltip"));
    checkBox.setSelected(initial == null ?
                         taskManager.getState().trackContextForNewChangelist :
                         taskManager.getAssociatedTask(initial) != null);
    bottomPanel.add(checkBox);
    return changeList -> {
      if (initial == null) {
        taskManager.getState().trackContextForNewChangelist = checkBox.isSelected();
        if (checkBox.isSelected()) {
          taskManager.trackContext(changeList);
        }
        else {
          taskManager.getActiveTask().addChangelist(new ChangeListInfo(changeList));
        }
      }
      else {
        final LocalTask associatedTask = taskManager.getAssociatedTask(changeList);
        if (checkBox.isSelected()) {
          if (associatedTask == null) {
            taskManager.trackContext(changeList);
          }
        }
        else {
          if (associatedTask != null) {
            taskManager.removeTask(associatedTask);
          }
        }
      }
    };
  }
}
