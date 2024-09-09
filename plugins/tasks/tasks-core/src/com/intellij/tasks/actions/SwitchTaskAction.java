// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.tasks.ChangeListInfo;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.config.TaskSettings;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
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
  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull final Presentation presentation, @NotNull String place) {
    return new ComboBoxButton(presentation) {
      @Override
      protected @NotNull JBPopup createPopup(Runnable onDispose) {
        return SwitchTaskAction.createPopup(DataManager.getInstance().getDataContext(this), onDispose, false);
      }

      @Override
      public Dimension getMinimumSize() {
        var result = super.getMinimumSize();
        var font = getFont();
        if (font == null) return result;
        result.width = UIUtil.computeTextComponentMinimumSize(result.width, getText(), getFontMetrics(font), 4);
        return result;
      }
    };
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null || project.isDefault() || project.isDisposed()) {
      presentation.setEnabledAndVisible(false);
      presentation.setText("");
      presentation.setIcon(null);
    }
    else if (e.isFromActionToolbar()) {
      TaskManager taskManager = TaskManager.getManager(project);
      LocalTask activeTask = taskManager.getActiveTask();

      if (isTaskManagerComboInToolbarEnabledAndVisible(activeTask, taskManager)) {
        String s = getText(activeTask);
        presentation.setEnabledAndVisible(true);
        presentation.setText(s, false);
        presentation.setIcon(activeTask.getIcon());
        presentation.setDescription(activeTask.getSummary());
      }
      else {
        presentation.setEnabledAndVisible(false);
      }
    }
    else {
      presentation.setEnabledAndVisible(true);
      presentation.copyFrom(getTemplatePresentation());
    }
  }

  public static boolean isTaskManagerComboInToolbarEnabledAndVisible(LocalTask activeTask, TaskManager taskManager) {
    return !isOriginalDefault(activeTask) ||
           ContainerUtil.exists(taskManager.getAllRepositories(), repository -> !repository.isShared()) ||
           TaskSettings.getInstance().ALWAYS_DISPLAY_COMBO;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static boolean isOriginalDefault(LocalTask activeTask) {
    return activeTask.isDefault() && Comparing.equal(activeTask.getCreated(), activeTask.getUpdated());
  }

  private static @NlsActions.ActionText String getText(LocalTask activeTask) {
    String text = activeTask.getPresentableName();
    return StringUtil.first(text, 50, true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    assert project != null;
    ListPopup popup = createPopup(dataContext, null, true);
    popup.showCenteredInCurrentWindow(project);
  }

  private static ListPopup createPopup(@NotNull DataContext dataContext,
                                       @Nullable Runnable onDispose,
                                       boolean withTitle) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final Ref<Boolean> shiftPressed = Ref.create(false);
    List<TaskListItem> items = project == null ? Collections.emptyList() :
                               createPopupActionGroup(project, shiftPressed, PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext));
    final String title = withTitle ? TaskBundle.message("popup.title.switch.to.task") : null;
    ListPopupStep<TaskListItem> step = new MultiSelectionListPopupStep<>(title, items) {
      @Override
      public PopupStep<?> onChosen(List<TaskListItem> selectedValues, boolean finalChoice) {
        if (finalChoice) {
          selectedValues.get(0).select();
          return FINAL_CHOICE;
        }
        ActionGroup group = createActionsStep(selectedValues, project, shiftPressed);
        DataContext dataContext = DataContext.EMPTY_CONTEXT;
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
      public boolean hasSubstep(List<? extends TaskListItem> selectedValues) {
        return selectedValues.size() > 1 || selectedValues.get(0).getTask() != null;
      }

      @Override
      public boolean isSpeedSearchEnabled() {
        return true;
      }
    };

    final ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
    if (onDispose != null) {
      Disposer.register(popup, new Disposable() {
        @Override
        public void dispose() {
          onDispose.run();
        }
      });
    }
    if (items.size() <= 2) {
      return popup;
    }

    popup.setAdText(TaskBundle.message("popup.advertisement.press.shift.to.merge.with.current.context"), SwingConstants.LEFT);

    var popupImpl = (popup instanceof ListPopupImpl) ? (ListPopupImpl)popup : null;
    if (popupImpl == null) return popup;
    //todo: RDCT-627
    popupImpl.registerAction("shiftPressed", KeyStroke.getKeyStroke("shift pressed SHIFT"), new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        shiftPressed.set(true);
        popup.setCaption(TaskBundle.message("popup.title.merge.with.current.context"));
      }
    });
    popupImpl.registerAction("shiftReleased", KeyStroke.getKeyStroke("released SHIFT"), new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        shiftPressed.set(false);
        popup.setCaption(TaskBundle.message("popup.title.switch.to.task"));
      }
    });
    popupImpl.registerAction("invoke", KeyStroke.getKeyStroke("shift ENTER"), new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        popup.handleSelect(true);
      }
    });
    return popup;
  }

  private static ActionGroup createActionsStep(final List<TaskListItem> tasks, final Project project, final Ref<Boolean> shiftPressed) {
    DefaultActionGroup group = new DefaultActionGroup();
    final TaskManager manager = TaskManager.getManager(project);
    final LocalTask task = tasks.get(0).getTask();
    if (tasks.size() == 1 && task != null) {
      group.add(new DumbAwareAction(TaskBundle.message("action.switch.to.text")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          manager.activateTask(task, !shiftPressed.get());
        }
      });
      group.add(new DumbAwareAction(TaskBundle.message("action.edit.text.with.mnemonic")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          EditTaskDialog.editTask((LocalTaskImpl)task, project);
        }
      });
    }
    final AnAction remove = new DumbAwareAction(TaskBundle.message("action.remove.text")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
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

      @Override
      public ShortcutSet getShortcut() {
        return gotoTaskAction.getShortcutSet();
      }
    });

    final TaskManager manager = TaskManager.getManager(project);
    LocalTask activeTask = manager.getActiveTask();
    List<LocalTask> localTasks = manager.getLocalTasks();
    localTasks.sort(TaskManagerImpl.TASK_UPDATE_COMPARATOR);
    ArrayList<LocalTask> temp = new ArrayList<>();
    for (final LocalTask task : localTasks) {
      if (task == activeTask) {
        continue;
      }
      if (task.isClosed()) {
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

        group.add(new TaskListItem(task, i == 0 ? TaskBundle.message("separator.recently.closed.tasks") : null, true) {
          @Override
          void select() {
            manager.activateTask(task, !shiftPressed.get());
          }
        });
      }
    }
    return group;
  }

  public static void removeTask(final @NotNull Project project, @NotNull LocalTask task, @NotNull TaskManager manager) {
    if (task.isDefault()) {
      Messages.showInfoMessage(project, TaskBundle.message("dialog.message.default.task.cannot.be.removed"),
                               TaskBundle.message("dialog.title.cannot.remove"));
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
                                                      TaskBundle.message("dialog.message.changelist.associated.with.not.empty", task.getSummary()),
                                                      TaskBundle.message("dialog.title.changelist.not.empty"), Messages.getWarningIcon());
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