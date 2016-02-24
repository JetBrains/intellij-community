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
package com.intellij.tasks.impl;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskControlPanelProvider;
import com.intellij.tasks.TaskRepository;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Set;

/**
 * @author Dmitry Kachurin
 */
public class BaseCloseTaskControlPanelProvider implements TaskControlPanelProvider {

  private ComboBox myStateComboBox = new ComboBox();
  private JBCheckBox myUpdateState = new JBCheckBox("Update issue state");

  @Override
  public JPanel createControlPanel() {
    myUpdateState.setSelected(false);
    myUpdateState.setMnemonic('U');
    myStateComboBox.setEnabled(false);
    myStateComboBox.setMinimumAndPreferredWidth(300);

    myUpdateState.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myStateComboBox.setEnabled(myUpdateState.isSelected());
      }
    });

    myUpdateState.setVisible(false);
    myStateComboBox.setVisible(false);

    return FormBuilder.createFormBuilder()
      .addLabeledComponent(myUpdateState, myStateComboBox)
      .getPanel();
  }

  @Override
  public void doOpenDialog(final Task task, final Project project) {
    if (task == null || !task.isIssue()) {
      return;
    }
    final TaskRepository repository = task.getRepository();
    if (repository != null && repository.isSupported(TaskRepository.STATE_UPDATING)) {
      myStateComboBox.setVisible(true);
      myUpdateState.setVisible(true);
      ProgressManager.getInstance().run(new com.intellij.openapi.progress.Task.Backgroundable(project, "Fetching Available Task States", false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            final Set<CustomTaskState> customTaskStateSet = repository.getAvailableTaskStates(task);
            final CustomTaskState preferredTaskState = repository.getPreferredCloseTaskState();

            SwingUtilities.invokeAndWait(new Runnable() {
              @Override
              public void run() {
                myStateComboBox.setModel(new DefaultComboBoxModel(customTaskStateSet.toArray(new CustomTaskState[customTaskStateSet.size()])));
                myStateComboBox.setRenderer(new ColoredListCellRenderer<CustomTaskState>() {
                  @Override
                  protected void customizeCellRenderer(JList list, CustomTaskState value, int index, boolean selected, boolean hasFocus) {
                    append(value.getPresentableName());
                  }
                });
                if (customTaskStateSet.contains(preferredTaskState)) {
                  myStateComboBox.setSelectedItem(preferredTaskState);
                }
              }
            });
          } catch (final Exception e) {
            //
          }
        }
      });
    }
  }

  @Override
  public void doOKAction(final Task task, final Project project) {
    if (task == null || !task.isIssue()) {
      return;
    }
    final TaskRepository repository = task.getRepository();
    if (repository != null && repository.isSupported(TaskRepository.STATE_UPDATING)) {
      if (myUpdateState.isSelected()) {
        if (myStateComboBox.getSelectedItem() != null && myStateComboBox.getSelectedItem() instanceof CustomTaskState) {
          try {
            repository.setTaskState(task, (CustomTaskState) myStateComboBox.getSelectedItem());
            repository.setPreferredCloseTaskState((CustomTaskState)myStateComboBox.getSelectedItem());
          } catch (final Exception e) {
            Messages.showErrorDialog(project, e.getMessage(), "Cannot Set State For Issue");
          }
        }
      }
    }
  }
}
