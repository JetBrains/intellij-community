/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.tasks.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsTaskHandler;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.tasks.BranchInfo;
import com.intellij.tasks.ChangeListInfo;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.ui.CollectionComboBoxModel;
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
  private ComboBox myBranch;
  private JBLabel myChangelistLabel;
  private ComboBox myChangelist;

  public static void editTask(LocalTaskImpl task, Project project) {
    new EditTaskDialog(project, task).show();
  }

  protected EditTaskDialog(Project project, LocalTaskImpl task) {
    super(project);
    myTask = task;
    setTitle("Edit Task " + (task.isIssue() ? task.getPresentableId() : ""));

//    mySummary.putClientProperty(DialogWrapperPeer.HAVE_INITIAL_SELECTION, "");
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
      List<LocalChangeList> changeLists = changeListManager.getChangeLists();
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
    init();
  }

  @Override
  protected void doOKAction() {
    myTask.setSummary(mySummary.getText());
    if (myChangelist.isVisible()) {
      List<ChangeListInfo> changeLists = myTask.getChangeLists();
      changeLists.clear();
      LocalChangeList item = (LocalChangeList)myChangelist.getSelectedItem();
      if (item != null) {
        changeLists.add(new ChangeListInfo(item));
      }
    }
    if (myBranch.isVisible()) {
      List<BranchInfo> branches = myTask.getBranches();
      branches.clear();
      VcsTaskHandler.TaskInfo branch = (VcsTaskHandler.TaskInfo)myBranch.getSelectedItem();
      if (branch != null) {
        List<BranchInfo> infos = BranchInfo.fromTaskInfo(branch, false);
        branches.addAll(infos);
      }
    }
    close(OK_EXIT_CODE);
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return mySummary;
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
