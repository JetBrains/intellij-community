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

import com.intellij.ide.actions.TemplateKindCombo;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.binding.BindControl;
import com.intellij.openapi.options.binding.ControlBinder;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsTaskHandler;
import com.intellij.tasks.*;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.tasks.impl.TaskUiUtil.ComboBoxUpdater;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Function;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class OpenTaskDialog extends DialogWrapper {
  private final static Logger LOG = Logger.getInstance("#com.intellij.tasks.actions.SimpleOpenTaskDialog");
  private static final String START_FROM_BRANCH = "start.from.branch";

  private static final CustomTaskState DO_NOT_UPDATE_STATE = new CustomTaskState("", "-- do not update --");

  private JPanel myPanel;
  @BindControl(value = "clearContext", instant = true)
  private JCheckBox myClearContext;
  private JLabel myTaskNameLabel;
  private JPanel myVcsPanel;
  private JTextField myBranchName;
  private JTextField myChangelistName;
  private JBCheckBox myCreateBranch;
  private JBCheckBox myCreateChangelist;
  private JBLabel myFromLabel;
  private ComboBox myBranchFrom;
  private TemplateKindCombo myStateCombo;
  private JLabel myStateComboLabel;
  private JBLabel myStateComboHint;

  private final Project myProject;
  private final Task myTask;
  private VcsTaskHandler myVcsTaskHandler;

  public OpenTaskDialog(@NotNull final Project project, @NotNull final Task task) {
    super(project, false);
    myProject = project;
    myTask = task;
    TaskManagerImpl taskManager = (TaskManagerImpl)TaskManager.getManager(myProject);
    setTitle("Open Task");
    myTaskNameLabel.setText(TaskUtil.getTrimmedSummary(task));
    myTaskNameLabel.setIcon(task.getIcon());

    TaskManagerImpl manager = (TaskManagerImpl)TaskManager.getManager(project);
    ControlBinder binder = new ControlBinder(manager.getState());
    binder.bindAnnotations(this);
    binder.reset();

    myStateComboLabel.setLabelFor(myStateCombo);
    myStateComboHint.setIcon(PlatformIcons.UP_DOWN_ARROWS);

    final JComboBox comboBox = myStateCombo.getComboBox();
    comboBox.setPreferredSize(new Dimension(300, UIUtil.fixComboBoxHeight(comboBox.getPreferredSize().height)));
    final ListCellRenderer defaultRenderer = comboBox.getRenderer();
    //noinspection GtkPreferredJComboBoxRenderer
    comboBox.setRenderer(new ListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        if (value == null) {
          return new ListCellRendererWrapper<CustomStateTrinityAdapter>() {
            @Override
            public void customize(JList list, CustomStateTrinityAdapter value, int index, boolean selected, boolean hasFocus) {
              setText("-- no states available --");
            }
          }.getListCellRendererComponent(list, null, index, isSelected, cellHasFocus);
        }
        return defaultRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      }
    });


    // Capture correct modality state
    final TaskRepository repository = myTask.getRepository();
    if (myTask.isIssue() && repository != null && repository.isSupported(TaskRepository.STATE_UPDATING)) {
      // Find out proper way to determine modality state here
      new ComboBoxUpdater<CustomStateTrinityAdapter>(myProject, "Fetching available task states...", comboBox) {
        @NotNull
        @Override
        protected List<CustomStateTrinityAdapter> fetch(@NotNull ProgressIndicator indicator) throws Exception {
          return ContainerUtil
            .map(repository.getAvailableTaskStates(myTask), new Function<CustomTaskState, CustomStateTrinityAdapter>() {
              @Override
              public CustomStateTrinityAdapter fun(CustomTaskState state) {
                return new CustomStateTrinityAdapter(state);
              }
            });
        }

        @Nullable
        @Override
        public CustomStateTrinityAdapter getSelectedItem() {
          final CustomTaskState state = repository.getPreferredOpenTaskState();
          return state != null ? new CustomStateTrinityAdapter(state) : null;
        }

        @Nullable
        @Override
        public CustomStateTrinityAdapter getExtraItem() {
          return new CustomStateTrinityAdapter(DO_NOT_UPDATE_STATE);
        }
      }.queue();
    }
    else {
      myStateComboLabel.setVisible(false);
      myStateComboHint.setVisible(false);
      myStateCombo.setVisible(false);
    }

    TaskManagerImpl.Config state = taskManager.getState();
    myClearContext.setSelected(state.clearContext);

    AbstractVcs vcs = manager.getActiveVcs();
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
      myCreateChangelist.setSelected(manager.getState().createChangelist);

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
          VcsTaskHandler.TaskInfo[] tasks = handler.getCurrentTasks();
          if (tasks.length > 0) {
            myVcsTaskHandler = handler;
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
              info = tasks[0];
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
        myCreateBranch.setSelected(manager.getState().createBranch && myBranchFrom.getItemCount() > 0);
        myBranchFrom.setRenderer(new ColoredListCellRenderer<VcsTaskHandler.TaskInfo>() {
          @Override
          protected void customizeCellRenderer(JList list, VcsTaskHandler.TaskInfo value, int index, boolean selected, boolean hasFocus) {
            if (value != null) {
              append(value.getName());
            }
          }
        });
      }

      myBranchName.setText(taskManager.suggestBranchName(task));
      myChangelistName.setText(taskManager.getChangelistName(task));
      updateFields(true);
    }
    final JComponent contentPanel = getContentPanel();
    contentPanel.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        super.keyPressed(e);
      }
    });
    myStateCombo.registerUpDownHint(getPreferredFocusedComponent());
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

    final TaskRepository repository = myTask.getRepository();
    final CustomTaskState taskState = ((CustomStateTrinityAdapter)myStateCombo.getComboBox().getSelectedItem()).myState;
    if (repository != null && taskState != null && taskState != DO_NOT_UPDATE_STATE) {
      try {
        repository.setTaskState(myTask, taskState);
        repository.setPreferredOpenTaskState(taskState);
      }
      catch (Exception ex) {
        Messages.showErrorDialog(myProject, ex.getMessage(), "Cannot Set State For Issue");
        LOG.warn(ex);
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
    else {
      return myStateCombo.getComboBox();
    }
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  private static class CustomStateTrinityAdapter extends Trinity<String, Icon, String> {
    final CustomTaskState myState;

    public CustomStateTrinityAdapter(@NotNull CustomTaskState state) {
      super(state.getPresentableName(), null, state.getId());
      myState = state;
    }
  }
}
