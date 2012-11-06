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
import com.intellij.tasks.*;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.tasks.impl.TaskUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class SimpleOpenTaskDialog extends DialogWrapper {
  private final static Logger LOG = Logger.getInstance("#com.intellij.tasks.actions.SimpleOpenTaskDialog");

  private JPanel myPanel;
  @BindControl(value = "clearContext", instant = true)
  private JCheckBox myClearContext;
  @BindControl(value = "createChangelist", instant = true)
  private JCheckBox myCreateChangelist;
  private JCheckBox myMarkAsInProgressBox;
  private JLabel myTaskNameLabel;

  private final Project myProject;
  private final Task myTask;

  public SimpleOpenTaskDialog(@NotNull final Project project, @NotNull final Task task) {
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
    if (repository == null || !repository.getRepositoryType().getPossibleTaskStates().contains(TaskState.IN_PROGRESS)) {
      myMarkAsInProgressBox.setVisible(false);
    }

    myClearContext.setSelected(taskManager.getState().clearContext);

    if (!manager.isVcsEnabled()) {
      myCreateChangelist.setEnabled(false);
      myCreateChangelist.setSelected(false);
    }
    else if (task instanceof LocalTask && !((LocalTask)task).isClosedLocally()) {
      myCreateChangelist.setSelected(true);
      myCreateChangelist.setEnabled(false);
    }
    else {
      myCreateChangelist.setSelected(taskManager.getState().createChangelist);
      myCreateChangelist.setEnabled(true);
    }
    init();
    getPreferredFocusedComponent();
  }

  @Override
  protected void doOKAction() {
    TaskManagerImpl taskManager = (TaskManagerImpl)TaskManager.getManager(myProject);

    taskManager.getState().markAsInProgress = isMarkAsInProgress();
    if (taskManager.isVcsEnabled() && !(myTask instanceof LocalTask && !((LocalTask)myTask).isClosedLocally())) {
      taskManager.getState().createChangelist = myCreateChangelist.isSelected();
    }

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
    taskManager.activateTask(myTask, isClearContext(), isCreateChangelist());
    if (myTask.getType() == TaskType.EXCEPTION && AnalyzeTaskStacktraceAction.hasTexts(myTask)) {
      AnalyzeTaskStacktraceAction.analyzeStacktrace(myTask, myProject);
    }
    super.doOKAction();
  }

  private boolean isClearContext() {
    return myClearContext.isSelected();
  }

  private boolean isCreateChangelist() {
    return myCreateChangelist.isSelected();
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
