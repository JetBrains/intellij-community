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
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.ui.components.JBCheckBox;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class CloseTaskDialog extends DialogWrapper {

  private JCheckBox myCommitChanges;
  private JCheckBox myCloseIssue;
  private JPanel myPanel;
  private JLabel myTaskLabel;
  private JBCheckBox myMergeBranches;
  private JPanel myVcsPanel;
  private final TaskManagerImpl myTaskManager;

  public CloseTaskDialog(Project project, LocalTask task) {
    super(project, false);

    setTitle("Close Task");
    myTaskLabel.setText(TaskUtil.getTrimmedSummary(task));
    myTaskLabel.setIcon(task.getIcon());

    TaskRepository repository = task.getRepository();
    boolean visible = task.isIssue() && repository != null && repository.getRepositoryType().getPossibleTaskStates().contains(TaskState.RESOLVED);
    myCloseIssue.setVisible(visible);

    myTaskManager = (TaskManagerImpl)TaskManager.getManager(project);
    myCloseIssue.setSelected(visible && myTaskManager.getState().closeIssue);

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

  boolean isCloseIssue() {
    return myCloseIssue.isSelected();
  }

  boolean isCommitChanges() {
    return myCommitChanges.isSelected();
  }

  boolean isMergeBranch() {
    return myMergeBranches.isSelected();
  }

  @Override
  protected void doOKAction() {
    myTaskManager.getState().closeIssue = isCloseIssue();
    if (myCommitChanges.isEnabled()) {
      myTaskManager.getState().commitChanges = isCommitChanges();
    }
    if (myMergeBranches.isEnabled()) {
      myTaskManager.getState().mergeBranch = isMergeBranch();
    }
    super.doOKAction();
  }
}
