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

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsType;
import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.tasks.impl.TaskStateCombo;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

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
  private TaskStateCombo myStateCombo;
  private JBCheckBox myUpdateState;
  private final TaskManagerImpl myTaskManager;
  private JPanel myAdditionPanel;

  private final TaskRepository.AdditionalPanel myAddition;

  public CloseTaskDialog(Project project, final LocalTask task) {
    super(project, false);
    myProject = project;
    myTask = task;
    
    myAddition = task.getRepository() != null ? task.getRepository().createCloseTaskAdditionalPanel() : null;

    setTitle("Close Task");
    myTaskLabel.setText(TaskUtil.getTrimmedSummary(task));
    myTaskLabel.setIcon(task.getIcon());

    if (!TaskStateCombo.stateUpdatesSupportedFor(task)) {
      myUpdateState.setVisible(false);
      myStateCombo.setVisible(false);
    }

    final boolean stateUpdatesEnabled = PropertiesComponent.getInstance(myProject).getBoolean(UPDATE_STATE_ENABLED);
    myUpdateState.setSelected(stateUpdatesEnabled);
    myStateCombo.setEnabled(stateUpdatesEnabled);
    myUpdateState.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final boolean selected = myUpdateState.isSelected();
        myStateCombo.setEnabled(selected);
        PropertiesComponent.getInstance(myProject).setValue(UPDATE_STATE_ENABLED, String.valueOf(selected));
        if (selected) {
          myStateCombo.scheduleUpdateOnce();
        }
      }
    });

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

    myStateCombo.showHintLabel(false);
    if (myUpdateState.isSelected()) {
      myStateCombo.scheduleUpdateOnce();
    }
    
    if (myAddition != null) {
      myAddition.onOpen(myTask);
      if (myAddition.getAdditionalPanel() != null) {
        myAdditionPanel.add(myAddition.getAdditionalPanel());
      }
    }
    
    init();
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myStateCombo.isVisible() && myUpdateState.isSelected() ? myStateCombo.getComboBox() : null;
  }

  @Nullable
  CustomTaskState getCloseIssueState() {
    return myUpdateState.isSelected() ? myStateCombo.getSelectedState() : null;
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
    if (myAddition != null) {
      myAddition.onClose(myTask);
    }
    super.doOKAction();
  }

  private void createUIComponents() {
    myStateCombo = new TaskStateCombo(myProject, myTask) {
      @Nullable
      @Override
      protected CustomTaskState getPreferredState(@NotNull TaskRepository repository, @NotNull Collection<CustomTaskState> available) {
        return repository.getPreferredCloseTaskState();
      }
    };
  }
}
