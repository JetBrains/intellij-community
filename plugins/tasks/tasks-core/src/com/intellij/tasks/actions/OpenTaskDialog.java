// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tasks.actions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.tasks.*;
import com.intellij.tasks.impl.*;
import com.intellij.tasks.ui.TaskDialogPanel;
import com.intellij.tasks.ui.TaskDialogPanelProvider;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class OpenTaskDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(OpenTaskDialog.class);
  private static final String UPDATE_STATE_ENABLED = "tasks.open.task.update.state.enabled";

  private JPanel myPanel;
  private JCheckBox myClearContext;
  private JBCheckBox myUpdateState;
  private TaskStateCombo myTaskStateCombo;
  private JPanel myAdditionalPanel;
  private JBTextField myNameField;

  private final Project myProject;
  private final LocalTaskImpl myTask;
  private final List<TaskDialogPanel> myPanels;

  public OpenTaskDialog(final @NotNull Project project, final @NotNull Task task) {
    super(project, false);
    myProject = project;
    myTask = new LocalTaskImpl(task);
    myTaskStateCombo.setProject(myProject);
    myTaskStateCombo.setTask(myTask);

    setTitle(TaskBundle.message("dialog.title.open.task"));
    myNameField.setText(TaskUtil.getTrimmedSummary(task));
    myNameField.setEnabled(!task.isIssue());

    TaskManagerImpl taskManager = (TaskManagerImpl)TaskManager.getManager(myProject);

    if (!TaskStateCombo.stateUpdatesSupportedFor(task)) {
      myUpdateState.setVisible(false);
      myTaskStateCombo.setVisible(false);
    }
    final boolean stateUpdatesEnabled = PropertiesComponent.getInstance(project).getBoolean(UPDATE_STATE_ENABLED, false);
    myUpdateState.setSelected(stateUpdatesEnabled);
    myUpdateState.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final boolean selected = myUpdateState.isSelected();
        PropertiesComponent.getInstance(project).setValue(UPDATE_STATE_ENABLED, String.valueOf(selected));
        updateFields();
        if (selected) {
          myTaskStateCombo.scheduleUpdateOnce();
        }
      }
    });

    TaskManagerImpl.Config state = taskManager.getState();
    myClearContext.setSelected(state.clearContext);
    myClearContext.addActionListener(e -> {
      state.clearContext = myClearContext.isSelected();
    });

    updateFields();
    if (myUpdateState.isSelected()) {
      myTaskStateCombo.scheduleUpdateOnce();
    }

    myAdditionalPanel.setLayout(new BoxLayout(myAdditionalPanel, BoxLayout.Y_AXIS));
    myPanels = TaskDialogPanelProvider.getOpenTaskPanels(project, myTask);
    for (TaskDialogPanel panel : myPanels) {
      myAdditionalPanel.add(panel.getPanel());
    }
    myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        LocalTaskImpl oldTask = new LocalTaskImpl(myTask);
        myTask.setSummary(myNameField.getText());
        for (TaskDialogPanel panel : myPanels) {
          panel.taskNameChanged(oldTask, myTask);
        }
      }
    });
    init();
  }

  private void updateFields() {
    myTaskStateCombo.setEnabled(myUpdateState.isSelected());
  }

  @Override
  protected void doOKAction() {
    createTask();
    super.doOKAction();
  }

  public void createTask() {
    final TaskManagerImpl taskManager = (TaskManagerImpl)TaskManager.getManager(myProject);

    if (myUpdateState.isSelected()) {
      final CustomTaskState taskState = myTaskStateCombo.getSelectedState();
      final TaskRepository repository = myTask.getRepository();
      if (repository != null && taskState != null) {
        try {
          repository.setTaskState(myTask, taskState);
          repository.setPreferredOpenTaskState(taskState);
        }
        catch (Exception ex) {
          Messages.showErrorDialog(myProject, ex.getMessage(), TaskBundle.message("dialog.title.cannot.set.state.for.issue"));
          LOG.warn(ex);
        }
      }
    }

    for (TaskDialogPanel panel : myPanels) {
      panel.commit();
    }
    if (myTask.getRepository() != null) {
      TaskManagementUsageCollector.logOpenRemoteTask(myProject, myTask);
    }
    else {
      TaskManagementUsageCollector.logCreateLocalTaskManually(myProject);
    }
    taskManager.activateTask(myTask, isClearContext(), true);
    if (myTask.getType() == TaskType.EXCEPTION && AnalyzeTaskStacktraceAction.hasTexts(myTask)) {
      AnalyzeTaskStacktraceAction.analyzeStacktrace(myTask, myProject);
    }
  }

  private boolean isClearContext() {
    return myClearContext.isSelected();
  }

  @Override
  protected @NonNls String getDimensionServiceKey() {
    return "SimpleOpenTaskDialog";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    if (myNameField.getText().trim().isEmpty()) {
      return myNameField;
    }
    for (TaskDialogPanel panel : myPanels) {
      final JComponent component = panel.getPreferredFocusedComponent();
      if (component != null) {
        return component;
      }
    }
    if (myTaskStateCombo.isVisible() && myTaskStateCombo.isEnabled()){
      return myTaskStateCombo.getComboBox();
    }
    return null;
  }

  @Override
  protected @Nullable ValidationInfo doValidate() {
    String taskName = myNameField.getText().trim();
    if (taskName.isEmpty()) {
      return new ValidationInfo(TaskBundle.message("dialog.message.task.name.should.not.be.empty"), myNameField);
    }
    for (TaskDialogPanel panel : myPanels) {
      ValidationInfo validate = panel.validate();
      if (validate != null) return validate;
    }
    return null;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  private void createUIComponents() {
    myTaskStateCombo = new TaskStateCombo() {
      @Override
      protected @Nullable CustomTaskState getPreferredState(@NotNull TaskRepository repository, @NotNull Collection<CustomTaskState> available) {
        return repository.getPreferredOpenTaskState();
      }
    };
  }
}
