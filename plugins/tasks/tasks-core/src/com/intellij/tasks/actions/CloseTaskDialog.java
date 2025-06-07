// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tasks.actions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.impl.TaskStateCombo;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.tasks.ui.TaskDialogPanel;
import com.intellij.tasks.ui.TaskDialogPanelProvider;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class CloseTaskDialog extends DialogWrapper {
  private static final String UPDATE_STATE_ENABLED = "tasks.close.task.update.state.enabled";

  private final Project myProject;
  private final List<TaskDialogPanel> myPanels;

  private JPanel myPanel;
  private JLabel myTaskLabel;
  private TaskStateCombo myStateCombo;
  private JBCheckBox myUpdateState;
  private JPanel myAdditionalPanel;

  public CloseTaskDialog(Project project, final LocalTask task) {
    super(project, false);
    myProject = project;
    myStateCombo.setProject(myProject);
    myStateCombo.setTask(task);

    setTitle(TaskBundle.message("dialog.title.close.task"));
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

    myStateCombo.showHintLabel(false);
    if (myUpdateState.isSelected()) {
      myStateCombo.scheduleUpdateOnce();
    }

    myAdditionalPanel.setLayout(new BoxLayout(myAdditionalPanel, BoxLayout.Y_AXIS));
    myPanels = TaskDialogPanelProvider.getCloseTaskPanels(project, task);
    for (TaskDialogPanel panel : myPanels) {
      myAdditionalPanel.add(panel.getPanel());
    }

    init();
  }

  @Override
  protected void doOKAction() {
    for (TaskDialogPanel panel : myPanels) {
      panel.commit();
    }
    super.doOKAction();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myStateCombo.isVisible() && myUpdateState.isSelected() ? myStateCombo.getComboBox() : null;
  }

  @Nullable
  CustomTaskState getCloseIssueState() {
    return myUpdateState.isSelected() ? myStateCombo.getSelectedState() : null;
  }

  private void createUIComponents() {
    myStateCombo = new TaskStateCombo() {
      @Override
      protected @Nullable CustomTaskState getPreferredState(@NotNull TaskRepository repository, @NotNull Collection<CustomTaskState> available) {
        return repository.getPreferredCloseTaskState();
      }
    };
  }
}
