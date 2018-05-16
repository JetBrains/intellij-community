/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.playback.commands.ActionCommand;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.tasks.ChangeListInfo;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.config.TaskSettings;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.tools.SimpleActionGroup;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class SwitchTaskAction extends ComboBoxAction implements DumbAware {
  public JComponent createCustomComponent(final Presentation presentation) {
    return new ComboBoxButton(presentation) {
      @Override
      protected JBPopup createPopup(Runnable onDispose) {
        return SwitchTaskAction.createPopup(DataManager.getInstance().getDataContext(this), onDispose, false);
      }
    };
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    return new DefaultActionGroup();
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null || project.isDefault() || project.isDisposed()) {
      presentation.setEnabled(false);
      presentation.setText("");
      presentation.setIcon(null);
    }
    else {
      TaskManager taskManager = TaskManager.getManager(project);
      LocalTask activeTask = taskManager.getActiveTask();
      presentation.setVisible(true);
      presentation.setEnabled(true);

      if (isImplicit(activeTask) &&
          taskManager.getAllRepositories().length == 0 &&
          !TaskSettings.getInstance().ALWAYS_DISPLAY_COMBO) {
        presentation.setVisible(false);
      }
      else {
        String s = getText(activeTask);
        presentation.setText(s);
        presentation.setIcon(activeTask.getIcon());
        presentation.setDescription(activeTask.getSummary());
      }
    }
  }

  private static boolean isImplicit(LocalTask activeTask) {
    return activeTask.isDefault() && Comparing.equal(activeTask.getCreated(), activeTask.getUpdated());
  }

  private static String getText(LocalTask activeTask) {
    String text = activeTask.getPresentableName();
    return StringUtil.first(text, 50, true);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    assert project != null;
    ListPopupImpl popup = createPopup(dataContext, null, true);
    popup.showCenteredInCurrentWindow(project);
  }

  private static ListPopupImpl createPopup(@NotNull DataContext dataContext,
                                           @Nullable Runnable onDispose,
                                           boolean withTitle) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final Ref<Boolean> shiftPressed = Ref.create(false);
    final Ref<JComponent> componentRef = Ref.create();
    List<TaskListItem> items = project == null ? Collections.emptyList() :
                               createPopupActionGroup(project, shiftPressed, PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext));
    final String title = withTitle ? "Switch to Task" : null;
    ListPopupStep<TaskListItem> step = new MultiSelectionListPopupStep<TaskListItem>(title, items) {
      @Override
      public PopupStep<?> onChosen(List<TaskListItem> selectedValues, boolean finalChoice) {
        if (finalChoice) {
          selectedValues.get(0).select();
          return FINAL_CHOICE;
        }
        ActionGroup group = createActionsStep(selectedValues, project, shiftPressed);
        DataContext dataContext = DataManager.getInstance().getDataContext(componentRef.get());
        return JBPopupFactory.getInstance().createActionsStep(
          group, dataContext, null, false, false, null, null, true, 0, false);
      }

      @Override
      public Icon getIconFor(TaskListItem aValue) {
        return aValue.getIcon();
      }

      @NotNull
      @Override
      public String getTextFor(TaskListItem value) {
        return value.getText();
      }

      @Nullable
      @Override
      public ListSeparator getSeparatorAbove(TaskListItem value) {
        return value.getSeparator() == null ? null : new ListSeparator(value.getSeparator());
      }

      @Override
      public boolean hasSubstep(List<TaskListItem> selectedValues) {
        return selectedValues.size() > 1 || selectedValues.get(0).getTask() != null;
      }
    };

    final ListPopupImpl popup = (ListPopupImpl)JBPopupFactory.getInstance().createListPopup(step);
    if (onDispose != null) {
      Disposer.register(popup, new Disposable() {
        @Override
        public void dispose() {
          onDispose.run();
        }
      });
    }
    componentRef.set(popup.getComponent());
    if (items.size() <= 2) {
      return popup;
    }

    popup.setAdText("Press SHIFT to merge with current context");

    popup.registerAction("shiftPressed", KeyStroke.getKeyStroke("shift pressed SHIFT"), new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        shiftPressed.set(true);
        popup.setCaption("Merge with Current Context");
      }
    });
    popup.registerAction("shiftReleased", KeyStroke.getKeyStroke("released SHIFT"), new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        shiftPressed.set(false);
        popup.setCaption("Switch to Task");
      }
    });
    popup.registerAction("invoke", KeyStroke.getKeyStroke("shift ENTER"), new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        popup.handleSelect(true);
      }
    });
    return popup;
  }

  private static ActionGroup createActionsStep(final List<TaskListItem> tasks, final Project project, final Ref<Boolean> shiftPressed) {
    SimpleActionGroup group = new SimpleActionGroup();
    final TaskManager manager = TaskManager.getManager(project);
    final LocalTask task = tasks.get(0).getTask();
    if (tasks.size() == 1 && task != null) {
      group.add(new DumbAwareAction("&Switch to") {
        public void actionPerformed(AnActionEvent e) {
          manager.activateTask(task, !shiftPressed.get());
        }
      });
      group.add(new DumbAwareAction("&Edit") {
        @Override
        public void actionPerformed(AnActionEvent e) {
          EditTaskDialog.editTask((LocalTaskImpl)task, project);
        }
      });
    }
    final AnAction remove = new DumbAwareAction("&Remove") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        for (TaskListItem item : tasks) {
          LocalTask itemTask = item.getTask();
          if (itemTask != null) {
            removeTask(project, itemTask, manager);
          }
        }
      }
    };
    remove.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0)), null);
    group.add(remove);

    return group;
  }

  @NotNull
  private static List<TaskListItem> createPopupActionGroup(@NotNull final Project project,
                                                           final Ref<Boolean> shiftPressed,
                                                           final Component contextComponent) {
    List<TaskListItem> group = new ArrayList<>();

    final AnAction action = ActionManager.getInstance().getAction(GotoTaskAction.ID);
    assert action instanceof GotoTaskAction;
    final GotoTaskAction gotoTaskAction = (GotoTaskAction)action;
    group.add(new TaskListItem(gotoTaskAction.getTemplatePresentation().getText(),
                               gotoTaskAction.getTemplatePresentation().getIcon()) {
      @Override
      void select() {
        ActionManager.getInstance().tryToExecute(gotoTaskAction, ActionCommand.getInputEvent(GotoTaskAction.ID),
                                                 contextComponent, ActionPlaces.UNKNOWN, false);
      }
    });

    final TaskManager manager = TaskManager.getManager(project);
    LocalTask activeTask = manager.getActiveTask();
    List<LocalTask> localTasks = manager.getLocalTasks();
    Collections.sort(localTasks, TaskManagerImpl.TASK_UPDATE_COMPARATOR);
    ArrayList<LocalTask> temp = new ArrayList<>();
    for (final LocalTask task : localTasks) {
      if (task == activeTask) {
        continue;
      }
      if (manager.isLocallyClosed(task)) {
        temp.add(task);
        continue;
      }

      group.add(new TaskListItem(task, group.size() == 1 ? "" : null, false) {
        @Override
        void select() {
          manager.activateTask(task, !shiftPressed.get());
        }
      });
    }
    if (!temp.isEmpty()) {
      for (int i = 0, tempSize = temp.size(); i < Math.min(tempSize, 15); i++) {
        final LocalTask task = temp.get(i);

        group.add(new TaskListItem(task, i == 0 ? "Recently Closed Tasks" : null, true) {
          @Override
          void select() {
            manager.activateTask(task, !shiftPressed.get());
          }
        });
      }
    }
    return group;
  }

  public static void removeTask(final @NotNull Project project, LocalTask task, TaskManager manager) {
    if (task.isDefault()) {
      Messages.showInfoMessage(project, "Default task cannot be removed", "Cannot Remove");
    }
    else {

      List<ChangeListInfo> infos = task.getChangeLists();
      List<LocalChangeList> lists = ContainerUtil.mapNotNull(infos, (NullableFunction<ChangeListInfo, LocalChangeList>)changeListInfo -> {
        LocalChangeList changeList = ChangeListManager.getInstance(project).getChangeList(changeListInfo.id);
        return changeList != null && !changeList.isDefault() ? changeList : null;
      });

      boolean removeIt = true;
      l:
      for (LocalChangeList list : lists) {
        if (!list.getChanges().isEmpty()) {
          int result = Messages.showYesNoCancelDialog(project,
                                                      "Changelist associated with '" + task.getSummary() + "' is not empty.\n" +
                                                      "Do you want to remove it and move the changes to the active changelist?",
                                                      "Changelist Not Empty", Messages.getWarningIcon());
          switch (result) {
            case Messages.YES:
              break l;
            case Messages.NO:
              removeIt = false;
              break;
            default:
              return;
          }
        }
      }
      if (removeIt) {
        for (LocalChangeList list : lists) {
          ChangeListManager.getInstance(project).removeChangeList(list);
        }
      }
      manager.removeTask(task);
    }
  }
}
