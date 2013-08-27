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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.binding.BindControl;
import com.intellij.openapi.options.binding.ControlBinder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsType;
import com.intellij.tasks.*;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Dmitry Avdeev
 */
public class OpenTaskDialog extends DialogWrapper {
  private final static Logger LOG = Logger.getInstance("#com.intellij.tasks.actions.SimpleOpenTaskDialog");

  private JPanel myPanel;
  @BindControl(value = "clearContext", instant = true)
  private JCheckBox myClearContext;
  private JCheckBox myMarkAsInProgressBox;
  private JLabel myTaskNameLabel;
  private JPanel myVcsPanel;
  private JTextField myBranchName;
  private JTextField myChangelistName;
  private JBCheckBox myCreateBranch;
  private JBCheckBox myCreateChangelist;

  private final Project myProject;
  private final Task myTask;

  public OpenTaskDialog(@NotNull final Project project, @NotNull final Task task) {
    super(project, false);
    myProject = project;
    myTask = task;
    TaskManagerImpl taskManager = (TaskManagerImpl)TaskManager.getManager(myProject);
    setTitle("Open Task");
    myTaskNameLabel.setText(TaskUtil.getTrimmedSummary(task));
    myTaskNameLabel.setIcon(task.getIcon());

    TaskManagerImpl manager = (TaskManagerImpl)TaskManager.getManager(project);
    ControlBinder binder = new ControlBinder(manager.getState());
    binder.bindAnnotations(this);
    binder.reset();

    TaskRepository repository = task.getRepository();
    myMarkAsInProgressBox.setSelected(manager.getState().markAsInProgress);
    if (repository == null || !repository.getRepositoryType().getPossibleTaskStates().contains(TaskState.IN_PROGRESS)) {
      myMarkAsInProgressBox.setVisible(false);
    }

    TaskManagerImpl.Config state = taskManager.getState();
    myClearContext.setSelected(state.clearContext);

    AbstractVcs vcs = manager.getActiveVcs();
    if (vcs == null) {
      myVcsPanel.setVisible(false);
    }
    else {
      ActionListener listener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          updateFields();
        }
      };
      myCreateChangelist.addActionListener(listener);
      myCreateBranch.addActionListener(listener);
      myCreateChangelist.setSelected(manager.getState().createChangelist);
      myCreateBranch.setSelected(manager.getState().createBranch);

      if (vcs.getType() != VcsType.distributed) {
        myCreateBranch.setSelected(false);
        myCreateBranch.setVisible(false);
        myBranchName.setVisible(false);
      }

      myBranchName.setText(taskManager.suggestBranchName(task));
      myChangelistName.setText(taskManager.getChangelistName(task));
      updateFields();
    }
    init();
  }

  private void updateFields() {
    myBranchName.setEnabled(myCreateBranch.isSelected());
    myChangelistName.setEnabled(myCreateChangelist.isSelected());
  }


  @Override
  protected void doOKAction() {
    createTask();
    super.doOKAction();
  }

  public void createTask() {
    TaskManagerImpl taskManager = (TaskManagerImpl)TaskManager.getManager(myProject);

    taskManager.getState().markAsInProgress = isMarkAsInProgress();
    taskManager.getState().createChangelist = myCreateChangelist.isSelected();
    taskManager.getState().createBranch = myCreateBranch.isSelected();

    TaskRepository repository = myTask.getRepository();
    if (isMarkAsInProgress() && repository != null) {
      try {
        repository.setTaskState(myTask, TaskState.IN_PROGRESS);
      }
      catch (Exception ex) {
        Messages.showErrorDialog(myProject, "Could not set state for " + myTask.getId(), "Error");
        LOG.warn(ex);
      }
    }
    LocalTask activeTask = taskManager.getActiveTask();
    LocalTask localTask = taskManager.activateTask(myTask, isClearContext());
    if (myCreateChangelist.isSelected()) {
      taskManager.createChangeList(localTask, myChangelistName.getText());
    }
    if (myCreateBranch.isSelected()) {
      taskManager.createBranch(localTask, activeTask, myBranchName.getText());
    }
    if (myTask.getType() == TaskType.EXCEPTION && AnalyzeTaskStacktraceAction.hasTexts(myTask)) {
      AnalyzeTaskStacktraceAction.analyzeStacktrace(myTask, myProject);
    }
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    if (myCreateBranch.isSelected()) {
      String branchName = myBranchName.getText().trim();
      if (branchName.isEmpty()) {
        return new ValidationInfo("Branch name should not be empty", myBranchName);
      }
      else if (branchName.contains(" ")) {
        return new ValidationInfo("Branch name should not contain spaces");
      }
      else {
        return null;
      }
    }
    if (myCreateChangelist.isSelected()) {
      if (myChangelistName.getText().trim().isEmpty()) {
        return new ValidationInfo("Changelist name should not be empty");
      }
    }
    return null;
  }

  private boolean isClearContext() {
    return myClearContext.isSelected();
  }

  private boolean isMarkAsInProgress() {
    return myMarkAsInProgressBox.isSelected() && myMarkAsInProgressBox.isVisible();
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "SimpleOpenTaskDialog";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    if (myCreateBranch.isSelected()) {
      return myBranchName;
    }
    else if (myCreateChangelist.isSelected()) {
      return myChangelistName;
    }
    else return null;
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
