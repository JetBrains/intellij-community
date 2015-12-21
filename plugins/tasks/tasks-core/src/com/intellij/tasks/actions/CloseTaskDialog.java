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
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsType;
import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskControlPanelProvider;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.uiDesigner.core.GridConstraints;
import org.jetbrains.annotations.Nullable;

import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JComponent;

/**
 * @author Dmitry Avdeev
 */
public class CloseTaskDialog extends DialogWrapper {
  private static final String UPDATE_STATE_ENABLED = "tasks.close.task.update.state.enabled";

  private final Project myProject;
  private final LocalTask myTask;
  private JCheckBox myCommitChanges;
  private JPanel myPanel;
  private JLabel myTaskLabel;
  private JBCheckBox myMergeBranches;
  private JPanel myVcsPanel;
  private final TaskManagerImpl myTaskManager;
  private final TaskControlPanelProvider myCloseTaskPanelControlProvider;

  private JPanel myControlPanel;

  public CloseTaskDialog(Project project, final LocalTask task) {
    super(project, false);
    myProject = project;
    myTask = task;

    setTitle("Close Task");
    myTaskLabel.setText(TaskUtil.getTrimmedSummary(task));
    myTaskLabel.setIcon(task.getIcon());

    if (myTask.getRepository() != null) {
      myCloseTaskPanelControlProvider = myTask.getRepository().getCloseTaskControlPanelProvider();
    } else {
      myCloseTaskPanelControlProvider = null;
    }

    myTaskManager = (TaskManagerImpl)TaskManager.getManager(project);

    if (myTaskManager.isVcsEnabled()) {
      boolean hasChanges = !task.getChangeLists().isEmpty();
      myCommitChanges.setEnabled(hasChanges);
      myCommitChanges.setSelected(hasChanges && myTaskManager.getState().commitChanges);
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

    final GridConstraints gridConstraints = new GridConstraints();

    gridConstraints.setRow(0);
    gridConstraints.setColumn(0);
    gridConstraints.setRowSpan(1);
    gridConstraints.setColSpan(1);
    gridConstraints.setVSizePolicy(1);
    gridConstraints.setHSizePolicy(6);
    gridConstraints.setAnchor(0);
    gridConstraints.setFill(1);
    gridConstraints.setIndent(0);
    gridConstraints.setUseParentLayout(false);

    if (myCloseTaskPanelControlProvider != null) {
      final JPanel closeControlPanel = myCloseTaskPanelControlProvider.createControlPanel();
      if (closeControlPanel != null) {
        myControlPanel.add(closeControlPanel);
      }
      myCloseTaskPanelControlProvider.doOpenDialog(myTask, myProject);
    }
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @Nullable
  CustomTaskState getCloseIssueState() {
    return null;
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
    if (myCloseTaskPanelControlProvider != null) {
      myCloseTaskPanelControlProvider.doOKAction(myTask, myProject);
    }
    super.doOKAction();
  }
}
