/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.tasks.actions.vcs;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vcs.VcsTaskHandler;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.config.TaskSettings;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.tasks.ui.TaskDialogPanel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Locale;

/**
 * @author Dmitry Avdeev
 */
public class VcsOpenTaskPanel extends TaskDialogPanel {

  private JPanel myPanel;
  private JTextField myBranchName;
  private JTextField myChangelistName;
  private JBCheckBox myCreateBranch;
  private JBCheckBox myCreateChangelist;
  private ComboBox<VcsTaskHandler.TaskInfo> myBranchFrom;
  private JBLabel myFromLabel;
  private JBCheckBox myUseBranch;
  private ComboBox<VcsTaskHandler.TaskInfo> myUseBranchCombo;
  private JBCheckBox myShelveChanges;

  private VcsTaskHandler myVcsTaskHandler;
  private static final String START_FROM_BRANCH = "start.from.branch";
  private final TaskManagerImpl myTaskManager;
  private final Project myProject;
  private final LocalTask myTask;
  private final LocalTask myPreviousTask;

  public VcsOpenTaskPanel(Project project, LocalTask task) {

    myTaskManager = (TaskManagerImpl)TaskManager.getManager(project);
    myProject = project;
    myTask = task;
    myPreviousTask = myTaskManager.getActiveTask();
    ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateFields(false);
      }
    };
    myCreateChangelist.addActionListener(listener);
    myCreateBranch.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myCreateBranch.isSelected()) myUseBranch.setSelected(false);
        updateFields(false);
      }
    });
    myUseBranch.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myUseBranch.isSelected()) myCreateBranch.setSelected(false);
        updateFields(false);
      }
    });
    myCreateChangelist.setSelected(myTaskManager.getState().createChangelist);
    myShelveChanges.setSelected(myTaskManager.getState().shelveChanges);
    myChangelistName.setText(getChangelistName(task));

    VcsTaskHandler[] handlers = VcsTaskHandler.getAllHandlers(project);
    if (handlers.length == 0) {
      myCreateBranch.setSelected(false);
      myCreateBranch.setVisible(false);
      myBranchName.setVisible(false);
      myFromLabel.setVisible(false);
      myBranchFrom.setVisible(false);
      myUseBranch.setSelected(false);
      myUseBranch.setVisible(false);
      myUseBranchCombo.setVisible(false);
    }
    else {
      String branchName = getBranchName(task);
      for (VcsTaskHandler handler : handlers) {
        VcsTaskHandler.TaskInfo[] tasks = handler.getAllExistingTasks();
        if (tasks.length > 0) {
          myVcsTaskHandler = handler;
          Arrays.sort(tasks);
          //noinspection unchecked
          myBranchFrom.setModel(new DefaultComboBoxModel(tasks));
          myBranchFrom.setEnabled(true);

          myUseBranchCombo.setModel(new DefaultComboBoxModel<>(tasks));
          branchName = getBranchName(task); // adjust after setting myVcsTaskHandler
          for (VcsTaskHandler.TaskInfo info : tasks) {
            if (branchName.equals(info.getName()) || task.getSummary().equals(info.getName())) {
              myUseBranchCombo.setSelectedItem(info);
              myUseBranch.setSelected(true);
              break;
            }
          }
          final String startFrom = PropertiesComponent.getInstance(project).getValue(START_FROM_BRANCH);
          VcsTaskHandler.TaskInfo info = null;
          if (startFrom != null) {
            info = ContainerUtil.find(tasks, taskInfo -> startFrom.equals(taskInfo.getName()));
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
      if (!myUseBranch.isSelected()) {
        myCreateBranch.setSelected(myTaskManager.getState().createBranch && myBranchFrom.getItemCount() > 0);
        myUseBranch.setSelected(myTaskManager.getState().useBranch && myUseBranchCombo.getItemCount() > 0);
      }
      myBranchFrom.setRenderer(new TaskInfoCellRenderer(myBranchFrom));
      myUseBranchCombo.setRenderer(new TaskInfoCellRenderer(myUseBranchCombo));
      myBranchName.setText(branchName);
    }

    updateFields(true);
  }

  private String getChangelistName(Task task) {
    return myTaskManager.getChangelistName(task);
  }

  @NotNull
  private String getBranchName(Task task) {
    String branchName = myVcsTaskHandler != null
               ? myVcsTaskHandler.cleanUpBranchName(myTaskManager.constructDefaultBranchName(task))
               : myTaskManager.suggestBranchName(task);
    return TaskSettings.getInstance().LOWER_CASE_BRANCH ? branchName.toLowerCase(Locale.ENGLISH) : branchName;
  }

  private void updateFields(boolean initial) {
    if (!initial && myBranchFrom.getItemCount() == 0 && myCreateBranch.isSelected()) {
      Messages.showWarningDialog(myPanel, "Can't create branch if no commit exists.\nCreate a commit first.", "Cannot Create Branch");
      myCreateBranch.setSelected(false);
    }
    myBranchName.setEnabled(myCreateBranch.isSelected());
    myFromLabel.setEnabled(myCreateBranch.isSelected());
    myBranchFrom.setEnabled(myCreateBranch.isSelected());
    myUseBranchCombo.setEnabled(myUseBranch.isSelected());
    myChangelistName.setEnabled(myCreateChangelist.isSelected());
  }

  @NotNull
  @Override
  public JComponent getPanel() {
    return myPanel;
  }

  @Override
  public void commit() {
    myTaskManager.getState().createChangelist = myCreateChangelist.isSelected();
    myTaskManager.getState().shelveChanges = myShelveChanges.isSelected();
    myTaskManager.getState().createBranch = myCreateBranch.isSelected();
    myTaskManager.getState().useBranch = myUseBranch.isSelected();

    if (myShelveChanges.isSelected()) {
      myTaskManager.shelveChanges(myPreviousTask, myPreviousTask.getSummary());
    }
    if (myCreateChangelist.isSelected()) {
      myTaskManager.createChangeList(myTask, myChangelistName.getText());
    }
    else {
      ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
      String comment = TaskUtil.getChangeListComment(myTask);
      changeListManager.editComment(changeListManager.getDefaultListName(), comment);
    }
    if (myCreateBranch.isSelected()) {
      VcsTaskHandler.TaskInfo branchFrom = (VcsTaskHandler.TaskInfo)myBranchFrom.getSelectedItem();
      Runnable createBranch = () -> myTaskManager.createBranch(myTask, myPreviousTask, myBranchName.getText(), branchFrom);
      VcsTaskHandler.TaskInfo[] current = myVcsTaskHandler.getCurrentTasks();
      if (branchFrom != null && (current.length == 0 || !current[0].equals(branchFrom)))  {
        myVcsTaskHandler.switchToTask(branchFrom, createBranch);
      }
      else {
        createBranch.run();
      }
    }
    if (myUseBranch.isSelected()) {
      VcsTaskHandler.TaskInfo branch = (VcsTaskHandler.TaskInfo)myUseBranchCombo.getSelectedItem();
      if (branch != null) {
        VcsTaskHandler.TaskInfo[] tasks = myVcsTaskHandler.getCurrentTasks();
        TaskManagerImpl.addBranches(myPreviousTask, tasks, true);
        myVcsTaskHandler.switchToTask(branch, () -> TaskManagerImpl.addBranches(myTask, new VcsTaskHandler.TaskInfo[]{branch}, false));
      }
    }
  }

  @Nullable
  @Override
  public ValidationInfo validate() {
    if (myCreateBranch.isSelected()) {
      String branchName = myBranchName.getText().trim();
      if (branchName.isEmpty()) {
        return new ValidationInfo("Branch name should not be empty", myBranchName);
      }
      else if (myVcsTaskHandler != null) {
        return myVcsTaskHandler.isBranchNameValid(branchName)
               ? null
               : new ValidationInfo("Branch name is not valid; check your vcs branch name restrictions.", myBranchName);
      }
      else if (branchName.contains(" ")) {
        return new ValidationInfo("Branch name should not contain spaces", myBranchName);
      }
      else {
        return null;
      }
    }
    if (myCreateChangelist.isSelected()) {
      if (myChangelistName.getText().trim().isEmpty()) {
        return new ValidationInfo("Changelist name should not be empty", myChangelistName);
      }
    }
    return null;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    if (myCreateBranch.isSelected()) {
      return myBranchName;
    }
    else if (myUseBranch.isSelected()) {
      return myUseBranchCombo;
    }
    else if (myCreateChangelist.isSelected()) {
      return myChangelistName;
    }
    return null;
  }

  @Override
  public void taskNameChanged(Task oldTask, Task newTask) {
    if (getBranchName(oldTask).equals(myBranchName.getText())) {
      myBranchName.setText(getBranchName(newTask));
    }
    if (getChangelistName(oldTask).equals(myChangelistName.getText())) {
      myChangelistName.setText(getChangelistName(newTask));
    }
  }

  private static class TaskInfoCellRenderer extends ColoredListCellRenderer<VcsTaskHandler.TaskInfo> {
    TaskInfoCellRenderer(ComboBox from) {
      super(from);
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList list,
                                         VcsTaskHandler.TaskInfo value,
                                         int index,
                                         boolean selected,
                                         boolean hasFocus) {
      if (value != null) {
        append(value.getName());
      }
    }
  }
}
