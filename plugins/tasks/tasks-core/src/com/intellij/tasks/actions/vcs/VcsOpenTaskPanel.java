// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.actions.vcs;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsTaskHandler;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.config.TaskSettings;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.tasks.ui.TaskDialogPanel;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;

/**
 * @author Dmitry Avdeev
 */
public class VcsOpenTaskPanel extends TaskDialogPanel {

  private JPanel myPanel;
  private JTextField myBranchName;
  private JTextField myChangelistName;
  private JBRadioButton myCreateBranch;
  private JBCheckBox myCreateChangelist;
  private ComboBox myBranchFrom;
  private JBLabel myFromLabel;
  private JBRadioButton myUseBranch;
  private ComboBox<VcsTaskHandler.TaskInfo> myUseBranchCombo;
  private JBCheckBox myShelveChanges;
  private JBRadioButton myDoNotAssociateBranch;

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
    myBranchFrom.setMinimumAndPreferredWidth(JBUIScale.scale(150));
    myUseBranchCombo.setUsePreferredSizeAsMinimum(false);
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
        updateFields(false);
      }
    });
    myUseBranch.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateFields(false);
      }
    });
    myDoNotAssociateBranch.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateFields(false);
      }
    });
    myCreateChangelist.setSelected(myTaskManager.getState().createChangelist);
    myShelveChanges.setSelected(myTaskManager.getState().shelveChanges);
    myChangelistName.setText(getChangelistName(task));

    if (!ChangeListManager.getInstance(myProject).areChangeListsEnabled()) {
      myCreateChangelist.setVisible(false);
      myCreateChangelist.setSelected(false);
      myChangelistName.setVisible(false);
    }

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
      myDoNotAssociateBranch.setVisible(false);
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
      myBranchFrom.setRenderer(SimpleListCellRenderer.create("", VcsTaskHandler.TaskInfo::getName));
      myUseBranchCombo.setRenderer(SimpleListCellRenderer.create("", VcsTaskHandler.TaskInfo::getName));
      myBranchName.setText(branchName);
      ComboboxSpeedSearch.installOn(myBranchFrom);
      ComboboxSpeedSearch.installOn(myUseBranchCombo);
    }

    updateFields(true);
  }

  private String getChangelistName(Task task) {
    return myTaskManager.getChangelistName(task);
  }

  private @NotNull String getBranchName(Task task) {
    String branchName = myTaskManager.suggestBranchName(task, StringUtil.notNullize(TaskSettings.getInstance().REPLACE_SPACES));
    if (myVcsTaskHandler != null)
      myVcsTaskHandler.cleanUpBranchName(branchName);
    return TaskSettings.getInstance().LOWER_CASE_BRANCH ? StringUtil.toLowerCase(branchName) : branchName;
  }

  private void updateFields(boolean initial) {
    if (!initial && myBranchFrom.getItemCount() == 0 && myCreateBranch.isSelected()) {
      Messages.showWarningDialog(myPanel, TaskBundle.message("dialog.message.can.t.create.branch.if.no.commit.exists.create.commit.first"),
                                 TaskBundle.message("dialog.title.cannot.create.branch"));
      myCreateBranch.setSelected(false);
    }
    myBranchName.setEnabled(myCreateBranch.isSelected());
    myFromLabel.setEnabled(myCreateBranch.isSelected());
    myBranchFrom.setEnabled(myCreateBranch.isSelected());
    myUseBranchCombo.setEnabled(myUseBranch.isSelected());
    myChangelistName.setEnabled(myCreateChangelist.isSelected());
  }

  @Override
  public @NotNull JComponent getPanel() {
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

  @Override
  public @Nullable ValidationInfo validate() {
    if (myCreateBranch.isSelected()) {
      String branchName = myBranchName.getText().trim();
      if (branchName.isEmpty()) {
        return new ValidationInfo(TaskBundle.message("dialog.message.branch.name.should.not.be.empty"), myBranchName);
      }
      else if (myVcsTaskHandler != null) {
        return myVcsTaskHandler.isBranchNameValid(branchName)
               ? null
               : new ValidationInfo(TaskBundle.message("dialog.message.branch.name.not.valid.check.your.vcs.branch.name.restrictions"), myBranchName);
      }
      else if (branchName.contains(" ")) {
        return new ValidationInfo(TaskBundle.message("dialog.message.branch.name.should.not.contain.spaces"), myBranchName);
      }
      else {
        return null;
      }
    }
    if (myCreateChangelist.isSelected()) {
      if (myChangelistName.getText().trim().isEmpty()) {
        return new ValidationInfo(TaskBundle.message("dialog.message.changelist.name.should.not.be.empty"), myChangelistName);
      }
    }
    return null;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
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
}
