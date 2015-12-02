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

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.binding.BindControl;
import com.intellij.openapi.options.binding.ControlBinder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsTaskHandler;
import com.intellij.tasks.*;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.tasks.impl.TaskStateCombo;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Collection;

/**
 * @author Dmitry Avdeev
 */
public class OpenTaskDialog extends DialogWrapper {
  private final static Logger LOG = Logger.getInstance("#com.intellij.tasks.actions.SimpleOpenTaskDialog");
  private static final String START_FROM_BRANCH = "start.from.branch";
  private static final String UPDATE_STATE_ENABLED = "tasks.open.task.update.state.enabled";

  private JPanel myPanel;
  @BindControl(value = "clearContext", instant = true)
  private JCheckBox myClearContext;
  private JLabel myTaskNameLabel;
  private JPanel myVcsPanel;
  private JTextField myBranchName;
  private JTextField myChangelistName;
  private JBCheckBox myCreateBranch;
  private JBCheckBox myCreateChangelist;
  private JBCheckBox myUpdateState;
  private JBLabel myFromLabel;
  private ComboBox myBranchFrom;
  private TaskStateCombo myTaskStateCombo;

  private final Project myProject;
  private final Task myTask;
  private VcsTaskHandler myVcsTaskHandler;

  public OpenTaskDialog(@NotNull final Project project, @NotNull final Task task) {
    super(project, false);
    myProject = project;
    myTask = task;
    setTitle("Open Task");
    myTaskNameLabel.setText(TaskUtil.getTrimmedSummary(task));
    myTaskNameLabel.setIcon(task.getIcon());

    TaskManagerImpl taskManager = (TaskManagerImpl)TaskManager.getManager(myProject);
    ControlBinder binder = new ControlBinder(taskManager.getState());
    binder.bindAnnotations(this);
    binder.reset();

    if (!TaskStateCombo.stateUpdatesSupportedFor(task)) {
      myUpdateState.setVisible(false);
      myTaskStateCombo.setVisible(false);
    }
    final boolean stateUpdatesEnabled = PropertiesComponent.getInstance(project).getBoolean(UPDATE_STATE_ENABLED, true);
    myUpdateState.setSelected(stateUpdatesEnabled);
    myUpdateState.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final boolean selected = myUpdateState.isSelected();
        PropertiesComponent.getInstance(project).setValue(UPDATE_STATE_ENABLED, String.valueOf(selected));
        updateFields(false);
        if (selected) {
          myTaskStateCombo.scheduleUpdateOnce();
        }
      }
    });

    TaskManagerImpl.Config state = taskManager.getState();
    myClearContext.setSelected(state.clearContext);

    AbstractVcs vcs = taskManager.getActiveVcs();
    if (vcs == null) {
      myVcsPanel.setVisible(false);
    }
    else {
      ActionListener listener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          updateFields(false);
        }
      };
      myCreateChangelist.addActionListener(listener);
      myCreateBranch.addActionListener(listener);
      myCreateChangelist.setSelected(taskManager.getState().createChangelist);

      VcsTaskHandler[] handlers = VcsTaskHandler.getAllHandlers(project);
      if (handlers.length == 0) {
        myCreateBranch.setSelected(false);
        myCreateBranch.setVisible(false);
        myBranchName.setVisible(false);
        myFromLabel.setVisible(false);
        myBranchFrom.setVisible(false);
      }
      else {
        for (VcsTaskHandler handler : handlers) {
          VcsTaskHandler.TaskInfo[] tasks = handler.getAllExistingTasks();
          if (tasks.length > 0) {
            myVcsTaskHandler = handler;
            Arrays.sort(tasks);
            //noinspection unchecked
            myBranchFrom.setModel(new DefaultComboBoxModel(tasks));
            myBranchFrom.setEnabled(true);
            final String startFrom = PropertiesComponent.getInstance(project).getValue(START_FROM_BRANCH);
            VcsTaskHandler.TaskInfo info = null;
            if (startFrom != null) {
              info = ContainerUtil.find(tasks, new Condition<VcsTaskHandler.TaskInfo>() {
                @Override
                public boolean value(VcsTaskHandler.TaskInfo taskInfo) {
                  return startFrom.equals(taskInfo.getName());
                }
              });
            }
            if (info == null) {
              VcsTaskHandler.TaskInfo[] current = handler.getCurrentTasks();
              info = current.length > 0 ? current[0] : tasks[0];
            }
            myBranchFrom.setSelectedItem(info);
            myBranchFrom.addActionListener(new ActionListener() {
              @Override
              public void actionPerformed(ActionEvent e) {
                VcsTaskHandler.TaskInfo item = (VcsTaskHandler.TaskInfo)myBranchFrom.getSelectedItem();
                if (item != null) {
                  PropertiesComponent.getInstance(project).setValue(START_FROM_BRANCH, item.getName());
                }
              }
            });
            break;
          }
        }
        myCreateBranch.setSelected(taskManager.getState().createBranch && myBranchFrom.getItemCount() > 0);
        myBranchFrom.setRenderer(new ColoredListCellRenderer<VcsTaskHandler.TaskInfo>() {
          @Override
          protected void customizeCellRenderer(JList list, VcsTaskHandler.TaskInfo value, int index, boolean selected, boolean hasFocus) {
            if (value != null) {
              append(value.getName());
            }
          }
        });
      }
      myBranchName.setText(myVcsTaskHandler != null
                           ? myVcsTaskHandler.cleanUpBranchName(taskManager.constructDefaultBranchName(task))
                           : taskManager.suggestBranchName(task));
      myChangelistName.setText(taskManager.getChangelistName(task));
    }
    updateFields(true);
    myTaskStateCombo.registerUpDownAction(myBranchName);
    myTaskStateCombo.registerUpDownAction(myChangelistName);
    if (myUpdateState.isSelected()) {
      myTaskStateCombo.scheduleUpdateOnce();
    }
    init();
  }

  private void updateFields(boolean initial) {
    if (!initial && myBranchFrom.getItemCount() == 0 && myCreateBranch.isSelected()) {
      Messages.showWarningDialog(myPanel, "Can't create branch if no commit exists.\nCreate a commit first.", "Cannot Create Branch");
      myCreateBranch.setSelected(false);
    }
    myBranchName.setEnabled(myCreateBranch.isSelected());
    myFromLabel.setEnabled(myCreateBranch.isSelected());
    myBranchFrom.setEnabled(myCreateBranch.isSelected());
    myChangelistName.setEnabled(myCreateChangelist.isSelected());
    myTaskStateCombo.setEnabled(myUpdateState.isSelected());
  }


  @Override
  protected void doOKAction() {
    createTask();
    super.doOKAction();
  }

  public void createTask() {
    final TaskManagerImpl taskManager = (TaskManagerImpl)TaskManager.getManager(myProject);

    taskManager.getState().createChangelist = myCreateChangelist.isSelected();
    taskManager.getState().createBranch = myCreateBranch.isSelected();

    if (myUpdateState.isSelected()) {
      final CustomTaskState taskState = myTaskStateCombo.getSelectedState();
      final TaskRepository repository = myTask.getRepository();
      if (repository != null && taskState != null) {
        try {
          repository.setTaskState(myTask, taskState);
          repository.setPreferredOpenTaskState(taskState);
        }
        catch (Exception ex) {
          Messages.showErrorDialog(myProject, ex.getMessage(), "Cannot Set State For Issue");
          LOG.warn(ex);
        }
      }
    }
    final LocalTask activeTask = taskManager.getActiveTask();
    final LocalTask localTask = taskManager.activateTask(myTask, isClearContext());
    if (myCreateChangelist.isSelected()) {
      taskManager.createChangeList(localTask, myChangelistName.getText());
    }
    if (myCreateBranch.isSelected()) {
      VcsTaskHandler.TaskInfo item = (VcsTaskHandler.TaskInfo)myBranchFrom.getSelectedItem();
      Runnable createBranch = new Runnable() {
        @Override
        public void run() {
          taskManager.createBranch(localTask, activeTask, myBranchName.getText());
        }
      };
      if (item != null) {
        myVcsTaskHandler.switchToTask(item, createBranch);
      }
      else {
        createBranch.run();
      }
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
      else if (myVcsTaskHandler != null) {
        return myVcsTaskHandler.isBranchNameValid(branchName)
               ? null
               : new ValidationInfo("Branch name is not valid; check your vcs branch name restrictions.");
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
    else if (myTaskStateCombo.isVisible() && myTaskStateCombo.isEnabled()){
      return myTaskStateCombo.getComboBox();
    }
    return null;
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  private void createUIComponents() {
    myTaskStateCombo = new TaskStateCombo(myProject, myTask) {
      @Nullable
      @Override
      protected CustomTaskState getPreferredState(@NotNull TaskRepository repository, @NotNull Collection<CustomTaskState> available) {
        return repository.getPreferredOpenTaskState();
      }
    };
  }
}
