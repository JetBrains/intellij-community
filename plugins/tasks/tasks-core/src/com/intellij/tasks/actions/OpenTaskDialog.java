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
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.RadioButtonEnumModel;
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
  private ButtonGroup myVcsGroup;

  private final Project myProject;
  private final Task myTask;
  private final RadioButtonEnumModel<TaskManager.VcsOperation> myButtonEnumModel;

  public OpenTaskDialog(@NotNull final Project project, @NotNull final Task task) {
    super(project, false);
    myProject = project;
    myTask = task;
    TaskManagerImpl taskManager = (TaskManagerImpl)TaskManager.getManager(myProject);
    setTitle("Open Task");
    myTaskNameLabel.setText(TaskUtil.getTrimmedSummary(task));

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

    myButtonEnumModel = RadioButtonEnumModel.bindEnum(TaskManager.VcsOperation.class, myVcsGroup);
    AbstractVcs vcs = manager.getActiveVcs();
    if (vcs == null) {
      myVcsPanel.setVisible(false);
    }
    else {
      if (state.vcsOperation == -1) {
        state.vcsOperation = vcs.getType() == VcsType.distributed
                             ? TaskManager.VcsOperation.CREATE_BRANCH.ordinal()
                             : TaskManager.VcsOperation.CREATE_CHANGELIST.ordinal();
      }
      myVcsPanel.setBorder(IdeBorderFactory.createTitledBorder(vcs.getDisplayName() + " operations", false));
      myBranchName.setText(taskManager.suggestBranchName(task));
      myChangelistName.setText(taskManager.getChangelistName(task));
      ActionListener listener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          TaskManager.VcsOperation selected = myButtonEnumModel.getSelected();
          myChangelistName.setEnabled(false);
          myBranchName.setEnabled(false);
          if (selected == TaskManager.VcsOperation.CREATE_BRANCH) {
            myBranchName.setEnabled(true);
            myBranchName.requestFocus();
          }
          else if (selected == TaskManager.VcsOperation.CREATE_CHANGELIST) {
            myChangelistName.setEnabled(true);
            myChangelistName.requestFocus();
          }
        }
      };
      myButtonEnumModel.addActionListener(listener);
      myButtonEnumModel.setSelected(state.vcsOperation);
      listener.actionPerformed(null);
    }
    init();
    getPreferredFocusedComponent();
  }

  @Override
  protected void doOKAction() {
    TaskManagerImpl taskManager = (TaskManagerImpl)TaskManager.getManager(myProject);

    taskManager.getState().markAsInProgress = isMarkAsInProgress();
    TaskManager.VcsOperation operation = getVcsOperation();
    taskManager.getState().vcsOperation = operation.ordinal();

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
    LocalTask localTask = taskManager.activateTask(myTask, isClearContext());
    LocalTask activeTask = taskManager.getActiveTask();
    taskManager.activateInVcs(localTask, activeTask, operation, myBranchName.getText());
    if (myTask.getType() == TaskType.EXCEPTION && AnalyzeTaskStacktraceAction.hasTexts(myTask)) {
      AnalyzeTaskStacktraceAction.analyzeStacktrace(myTask, myProject);
    }
    super.doOKAction();
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    switch (myButtonEnumModel.getSelected()) {
      case CREATE_BRANCH:
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
      case CREATE_CHANGELIST:
        if (myChangelistName.getText().trim().isEmpty()) {
          return new ValidationInfo("Changelist name should not be empty");
        }
      case DO_NOTHING:
        return null;
    }
    return null;
  }

  private boolean isClearContext() {
    return myClearContext.isSelected();
  }

  private TaskManager.VcsOperation getVcsOperation() {
    if (myVcsPanel.isVisible()) {
      return myButtonEnumModel.getSelected();
    }
    else {
      return TaskManager.VcsOperation.DO_NOTHING;
    }
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
    return null;
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
