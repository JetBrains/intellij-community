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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsType;
import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.tasks.impl.TaskStateCombo;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * @author Dmitry Avdeev
 */
public class CloseTaskDialog extends DialogWrapper {
  private final Project myProject;
  private final LocalTask myTask;
  private JCheckBox myCommitChanges;
  private JPanel myPanel;
  private JLabel myTaskLabel;
  private JBCheckBox myMergeBranches;
  private JPanel myVcsPanel;
  private JLabel myStateComboBoxLabel;
  private TaskStateCombo myStateCombo;
  private final TaskManagerImpl myTaskManager;

  public CloseTaskDialog(Project project, final LocalTask task) {
    super(project, false);
    myProject = project;
    myTask = task;

    setTitle("Close Task");
    myTaskLabel.setText(TaskUtil.getTrimmedSummary(task));
    myTaskLabel.setIcon(task.getIcon());

    myStateComboBoxLabel.setLabelFor(myStateCombo);
    if (!TaskStateCombo.isStateSupportedFor(task)) {
      myStateComboBoxLabel.setVisible(false);
      myStateCombo.setVisible(false);
    }

    myTaskManager = (TaskManagerImpl)TaskManager.getManager(project);

    if (myTaskManager.isVcsEnabled()) {
      myCommitChanges.setEnabled(!task.getChangeLists().isEmpty());
      myCommitChanges.setSelected(myTaskManager.getState().commitChanges);
      if (myTaskManager.getActiveVcs().getType() == VcsType.distributed) {
        boolean enabled = !task.getBranches(true).isEmpty() && !task.getBranches(false).isEmpty();
        myMergeBranches.setEnabled(enabled);
        myMergeBranches.setSelected(enabled && myTaskManager.getState().mergeBranch);
      }
      else {
        myMergeBranches.setVisible(false);
      }
    }
    else {
      myVcsPanel.setVisible(false);
    }
    final JComponent preferredFocusedComponent = getPreferredFocusedComponent();
    if (preferredFocusedComponent != null) {
      myStateCombo.registerUpDownAction(preferredFocusedComponent);
    }
    myStateCombo.scheduleUpdate();
    init();
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myStateCombo.getComboBox();
  }

  @Nullable
  CustomTaskState getCloseIssueState() {
    return myStateCombo.getSelectedState();
  }

  boolean isCommitChanges() {
    return myCommitChanges.isSelected();
  }

  boolean isMergeBranch() {
    return myMergeBranches.isSelected();
  }

  @Override
  protected void doOKAction() {
    if (myCommitChanges.isEnabled()) {
      myTaskManager.getState().commitChanges = isCommitChanges();
    }
    if (myMergeBranches.isEnabled()) {
      myTaskManager.getState().mergeBranch = isMergeBranch();
    }
    super.doOKAction();
  }

  private void createUIComponents() {
    myStateCombo = new TaskStateCombo(myProject, myTask) {
      @Nullable
      @Override
      protected CustomTaskState getPreferredState(@NotNull TaskRepository repository, @NotNull Collection<CustomTaskState> available) {
        return repository.getPreferredCloseTaskState();
      }
    };
  }
}
