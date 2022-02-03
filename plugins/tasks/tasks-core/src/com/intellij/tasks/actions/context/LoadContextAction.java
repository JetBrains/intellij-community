// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.tasks.actions.context;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.actions.BaseTaskAction;
import com.intellij.tasks.actions.SwitchTaskAction;
import com.intellij.tasks.context.ContextInfo;
import com.intellij.tasks.context.LoadContextUndoableAction;
import com.intellij.tasks.context.WorkingContextManager;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import icons.TasksCoreIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class LoadContextAction extends BaseTaskAction {

  private static final int MAX_ROW_COUNT = 10;

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = getProject(e);
    assert project != null;
    DefaultActionGroup group = new DefaultActionGroup();
    final WorkingContextManager manager = WorkingContextManager.getInstance(project);
    List<ContextInfo> history = manager.getContextHistory();
    List<ContextHolder> infos =
      new ArrayList<>(ContainerUtil.map2List(history, (Function<ContextInfo, ContextHolder>)info -> new ContextHolder() {
        @Override
        void load(final boolean clear) {
          LoadContextUndoableAction undoableAction = LoadContextUndoableAction.createAction(manager, clear, info.name);
          UndoableCommand.execute(project, undoableAction, TaskBundle.message("command.name.load.context", info.comment), "Context");
        }

        @Override
        void remove() {
          manager.removeContext(info.name);
        }

        @Override
        Date getDate() {
          return new Date(info.date);
        }

        @Override
        String getComment() {
          return info.comment;
        }

        @Override
        Icon getIcon() {
          return TasksCoreIcons.SavedContext;
        }
      }));
    final TaskManager taskManager = TaskManager.getManager(project);
    List<LocalTask> tasks = taskManager.getLocalTasks();
    infos.addAll(ContainerUtil.mapNotNull(tasks, (NullableFunction<LocalTask, ContextHolder>)task -> {
      if (task.isActive()) {
        return null;
      }
      return new ContextHolder() {
        @Override
        void load(boolean clear) {
          LoadContextUndoableAction undoableAction = LoadContextUndoableAction.createAction(manager, clear, task);
          UndoableCommand.execute(project, undoableAction,
                                  TaskBundle.message("command.name.load.context", TaskUtil.getTrimmedSummary(task)), "Context");
        }

        @Override
        void remove() {
          SwitchTaskAction.removeTask(project, task, taskManager);
        }

        @Override
        Date getDate() {
          return task.getUpdated();
        }

        @Override
        String getComment() {
          return TaskUtil.getTrimmedSummary(task);
        }

        @Override
        Icon getIcon() {
          return task.getIcon();
        }
      };
    }));

    infos.sort((o1, o2) -> o2.getDate().compareTo(o1.getDate()));

    final Ref<Boolean> shiftPressed = Ref.create(false);
    boolean today = true;
    Calendar now = Calendar.getInstance();
    for (final ContextHolder info : infos) {
      Calendar calendar = Calendar.getInstance();
      calendar.setTime(info.getDate());
      if (today &&
          (calendar.get(Calendar.YEAR) != now.get(Calendar.YEAR) ||
          calendar.get(Calendar.DAY_OF_YEAR) != now.get(Calendar.DAY_OF_YEAR))) {
        group.addSeparator();
        today = false;
      }
      group.add(createItem(info, shiftPressed));
    }

    final ListPopupImpl popup = (ListPopupImpl)JBPopupFactory.getInstance()
      .createActionGroupPopup(TaskBundle.message("popup.title.load.context"), group, e.getDataContext(), false, null, MAX_ROW_COUNT);
    popup.setAdText(TaskBundle.message("popup.advertisement.press.shift.to.merge.with.current.context"));
    popup.registerAction("shiftPressed", KeyStroke.getKeyStroke("shift pressed SHIFT"), new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        shiftPressed.set(true);
        popup.setCaption(TaskBundle.message("popup.title.merge.with.current.context"));
      }
    });
    popup.registerAction("shiftReleased", KeyStroke.getKeyStroke("released SHIFT"), new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        shiftPressed.set(false);
        popup.setCaption(TaskBundle.message("popup.title.load.context"));
      }
    });
    popup.registerAction("invoke", KeyStroke.getKeyStroke("shift ENTER"), new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        popup.handleSelect(true);
      }
    });
    popup.showCenteredInCurrentWindow(project);
  }

  abstract static class ContextHolder {

    abstract void load(boolean clear);
    abstract void remove();
    abstract Date getDate();
    abstract String getComment();
    abstract Icon getIcon();
  }

  private static ActionGroup createItem(final ContextHolder holder, final Ref<Boolean> shiftPressed) {
    String text = DateFormatUtil.formatPrettyDateTime(holder.getDate());
    String comment = holder.getComment();
    if (!StringUtil.isEmpty(comment)) {
      text = comment + " (" + text + ")";
    }
    final AnAction loadAction = new AnAction(TaskBundle.messagePointer("action.LoadContextAction.Anonymous.text.load")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        holder.load(!shiftPressed.get());
      }
    };
    ActionGroup contextGroup = new ActionGroup(text, text, holder.getIcon()) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        loadAction.actionPerformed(e);
      }

      @Override
      public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
        return new AnAction[]{loadAction,
          new AnAction(TaskBundle.messagePointer("action.LoadContextAction.Anonymous.text.remove")) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
              holder.remove();
            }
          }};
      }
    };
    contextGroup.setPopup(true);
    contextGroup.getTemplatePresentation().setPerformGroup(true);
    return contextGroup;
  }
}
