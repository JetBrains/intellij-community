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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsType;
import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.tasks.impl.TaskUiUtil;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class CloseTaskDialog extends DialogWrapper {
  private static final CustomTaskState DO_NOT_UPDATE_STATE = new CustomTaskState("", "-- do not update --");

  private JCheckBox myCommitChanges;
  private JPanel myPanel;
  private JLabel myTaskLabel;
  private JBCheckBox myMergeBranches;
  private JPanel myVcsPanel;
  private ComboBox myStateComboBox;
  private JLabel myStateComboBoxLabel;
  private final TaskManagerImpl myTaskManager;

  public CloseTaskDialog(Project project, final LocalTask task) {
    super(project, false);

    setTitle("Close Task");
    myTaskLabel.setText(TaskUtil.getTrimmedSummary(task));
    myTaskLabel.setIcon(task.getIcon());

    final TaskRepository repository = task.getRepository();
    myStateComboBox.setRenderer(new ListCellRendererWrapper<CustomTaskState>() {
      @Override
      public void customize(JList list, CustomTaskState value, int index, boolean selected, boolean hasFocus) {
        if (value != null) {
          setText(value.getPresentableName());
        }
        else {
          setText("-- no states available --");
        }
      }
    });

    // Capture correct modality state
    if (task.isIssue() && repository != null && repository.isSupported(TaskRepository.STATE_UPDATING)) {
      // Find out proper way to determine modality state here
      new TaskUiUtil.ComboBoxUpdater<CustomTaskState>(project, "Fetching available task states...", myStateComboBox) {
        @NotNull
        @Override
        protected Set<CustomTaskState> fetch(@NotNull ProgressIndicator indicator) throws Exception {
          return repository.getAvailableTaskStates(task);
        }

        @Nullable
        @Override
        public CustomTaskState getSelectedItem() {
          return repository.getPreferredCloseTaskState();
        }

        @Nullable
        @Override
        public CustomTaskState getExtraItem() {
          return DO_NOT_UPDATE_STATE;
        }
      }.queue();
    }
    else {
      myStateComboBoxLabel.setVisible(false);
      myStateComboBox.setVisible(false);
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
    init();
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Nullable
  CustomTaskState getCloseIssueState() {
    final CustomTaskState selected = (CustomTaskState)myStateComboBox.getSelectedItem();
    return selected == null || selected == DO_NOT_UPDATE_STATE ? null : selected;
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
    myStateComboBox = new ComboBox(300);
  }
}
