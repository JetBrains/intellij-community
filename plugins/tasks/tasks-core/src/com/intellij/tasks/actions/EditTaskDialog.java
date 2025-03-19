// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsTaskHandler;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.tasks.BranchInfo;
import com.intellij.tasks.ChangeListInfo;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class EditTaskDialog extends DialogWrapper {

  private final LocalTaskImpl myTask;

  private JPanel myPanel;
  private JTextField mySummary;
  private JBLabel myBranchLabel;
  private ComboBox<VcsTaskHandler.TaskInfo> myBranch;
  private JBLabel myChangelistLabel;
  private ComboBox<LocalChangeList> myChangelist;

  public static void editTask(LocalTaskImpl task, Project project) {
    new EditTaskDialog(project, task).show();
  }

  protected EditTaskDialog(Project project, LocalTaskImpl task) {
    super(project);
    myTask = task;
    setTitle(TaskBundle.message("dialog.title.edit.task.choice", task.getPresentableId(), task.isIssue() ? 0 : 1));

    mySummary.setText(task.getSummary());

    AbstractVcs vcs = TaskManager.getManager(project).getActiveVcs();
    if (vcs == null) {
      myBranchLabel.setVisible(false);
      myBranch.setVisible(false);
      myChangelistLabel.setVisible(false);
      myChangelist.setVisible(false);
    }
    else {
      ChangeListManager changeListManager = ChangeListManager.getInstance(project);
      if (changeListManager.areChangeListsEnabled()) {
        List<LocalChangeList> changeLists = new ArrayList<>(changeListManager.getChangeLists());
        changeLists.add(null);
        myChangelist.setModel(new CollectionComboBoxModel<>(changeLists));
        final List<ChangeListInfo> lists = task.getChangeLists();
        if (!lists.isEmpty()) {
          LocalChangeList list = changeListManager.getChangeList(lists.get(0).id);
          myChangelist.setSelectedItem(list);
        }
        else {
          myChangelist.setSelectedItem(null);
        }
      }
      else {
        myChangelistLabel.setVisible(false);
        myChangelist.setVisible(false);
      }

      VcsTaskHandler[] handlers = VcsTaskHandler.getAllHandlers(project);
      if (handlers.length == 0) {
        myBranchLabel.setVisible(false);
        myBranch.setVisible(false);
      }
      else {
        VcsTaskHandler.TaskInfo[] tasks = handlers[0].getAllExistingTasks();
        ArrayList<VcsTaskHandler.TaskInfo> infos = new ArrayList<>(Arrays.asList(tasks));
        Collections.sort(infos);
        infos.add(null);
        myBranch.setModel(new CollectionComboBoxModel<>(infos));
        final List<BranchInfo> branches = task.getBranches(false);
        if (!branches.isEmpty()) {
          VcsTaskHandler.TaskInfo info = ContainerUtil.find(tasks, info1 -> branches.get(0).name.equals(info1.getName()));
          myBranch.setSelectedItem(info);
        }
        else {
          myBranch.setSelectedItem(null);
        }
      }
    }
    ComboboxSpeedSearch.installOn(myBranch);
    ComboboxSpeedSearch.installOn(myChangelist);
    init();
  }

  @Override
  protected void doOKAction() {
    myTask.setSummary(mySummary.getText()); //NON-NLS
    if (myChangelist.isVisible()) {
      List<ChangeListInfo> changeLists = myTask.getChangeLists();
      changeLists.clear();
      LocalChangeList item = myChangelist.getItem();
      if (item != null) {
        changeLists.add(new ChangeListInfo(item));
      }
    }
    if (myBranch.isVisible()) {
      List<BranchInfo> branches = myTask.getBranches();
      branches.clear();
      VcsTaskHandler.TaskInfo branch = myBranch.getItem();
      if (branch != null) {
        List<BranchInfo> infos = BranchInfo.fromTaskInfo(branch, false);
        branches.addAll(infos);
      }
    }
    close(OK_EXIT_CODE);
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return mySummary;
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myPanel;
  }
}
