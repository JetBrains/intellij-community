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
import com.intellij.tasks.actions.OpenTaskDialog;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.TextFieldWithAutoCompletionContributor;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

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
    final OpenTaskDialog.MyTextFieldWithAutoCompletionListProvider completionProvider =
      new OpenTaskDialog.MyTextFieldWithAutoCompletionListProvider(myProject);

    TextFieldWithAutoCompletionContributor.installCompletion(document, myProject, completionProvider, false);
  }

  public Consumer<LocalChangeList> addControls(JPanel bottomPanel, @Nullable final LocalChangeList initial) {
    if (initial == null || myTaskManager.getAssociatedTask(initial) == null) {
      return addControlsForNotAssociatedChangelist(bottomPanel, initial);
    }
    else {
      return addControlsForAssociatedChangelist(bottomPanel, initial);
    }
  }

  private Consumer<LocalChangeList> addControlsForAssociatedChangelist(final JPanel bottomPanel, final LocalChangeList initial) {
    final JCheckBox checkBox = new JCheckBox("Disassociate from task");
    checkBox.setMnemonic('A');
    checkBox.setSelected(false);
    bottomPanel.add(Box.createHorizontalStrut(UIUtil.DEFAULT_HGAP));
    bottomPanel.add(checkBox);
    return new Consumer<LocalChangeList>() {
      public void consume(LocalChangeList changeList) {
        if (checkBox.isSelected()) {
          myTaskManager.disassociateFromTask(changeList);
        }
      }
    };
  }

  private Consumer<LocalChangeList> addControlsForNotAssociatedChangelist(final JPanel bottomPanel, @Nullable final LocalChangeList initial) {
    final JCheckBox checkBox = new JCheckBox("Associate with:");
    checkBox.setMnemonic('A');
    checkBox.setToolTipText("Reload context (e.g. open editors) when changelist is set active");

    ButtonGroup group = new ButtonGroup();
    final JRadioButton withCurrent = new JRadioButton("current task");
    withCurrent.setMnemonic('c');
    group.add(withCurrent);
    JRadioButton withNew = new JRadioButton("new task");
    withNew.setMnemonic('n');
    group.add(withNew);

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(withCurrent, BorderLayout.NORTH);
    panel.add(withNew, BorderLayout.SOUTH);

    bottomPanel.add(Box.createHorizontalStrut(UIUtil.DEFAULT_HGAP));
    bottomPanel.add(checkBox);
    bottomPanel.add(Box.createHorizontalStrut(UIUtil.DEFAULT_HGAP));
    bottomPanel.add(panel);

    checkBox.setSelected(myTaskManager.getState().associateWithTaskForNewChangelist);
    if (myTaskManager.getState().associateWithCurrentTaskForNewChangelist) {
      withCurrent.setSelected(true);
    }
    else {
      withNew.setSelected(true);
    }

    return new Consumer<LocalChangeList>() {
      public void consume(LocalChangeList changeList) {
        myTaskManager.getState().associateWithTaskForNewChangelist = checkBox.isSelected();
        myTaskManager.getState().associateWithCurrentTaskForNewChangelist = withCurrent.isSelected();
        if (checkBox.isSelected()) {
          myTaskManager.associateWithTask(changeList, withCurrent.isSelected());
        }
      }
    };
  }

  public void changelistCreated(LocalChangeList changeList) {
  }
}
