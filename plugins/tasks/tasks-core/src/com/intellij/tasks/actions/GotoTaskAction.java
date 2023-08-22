// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.actions;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.ide.actions.GotoActionBase;
import com.intellij.ide.util.gotoByName.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.doc.TaskPsiElement;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * @author Evgeny Zakrevsky
 */
public class GotoTaskAction extends GotoActionBase implements DumbAware {
  public static final CreateNewTaskAction CREATE_NEW_TASK_ACTION = new CreateNewTaskAction();
  public static final String ID = "tasks.goto";
  private static final Logger LOG = Logger.getInstance(GotoTaskAction.class);
  public static final int PAGE_SIZE = 20;

  public GotoTaskAction() {
    getTemplatePresentation().setText(TaskBundle.messagePointer("open.task.action.menu.text"));
    getTemplatePresentation().setIcon(IconUtil.getAddIcon());
  }

  @Override
  protected void gotoActionPerformed(@NotNull final AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return;
    perform(project);
  }

  void perform(final Project project) {
    final Ref<Boolean> shiftPressed = Ref.create(false);

    final ChooseByNamePopup popup = createPopup(project, new GotoTaskPopupModel(project), new TaskItemProvider(project));

    popup.setShowListForEmptyPattern(true);
    popup.setSearchInAnyPlace(true);
    popup.setAlwaysHasMore(true);
    popup.setAdText(
      TaskBundle.message("popup.advertisement.html.press.shift.to.merge.with.current.context.br.pressing.would.show.task.description.comments.html", KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_QUICK_JAVADOC))));
    popup.registerAction("shiftPressed", KeyStroke.getKeyStroke("shift pressed SHIFT"), new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        shiftPressed.set(true);
      }
    });
    popup.registerAction("shiftReleased", KeyStroke.getKeyStroke("released SHIFT"), new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        shiftPressed.set(false);
      }
    });

    final DefaultActionGroup group = new DefaultActionGroup(new ConfigureServersAction() {
      @Override
      protected void serversChanged() {
        popup.rebuildList(true);
      }
    });
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("GoToTask", group, true);
    actionToolbar.setTargetComponent(actionToolbar.getComponent());
    actionToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    actionToolbar.getComponent().setFocusable(false);
    actionToolbar.getComponent().setBorder(null);
    popup.setToolArea(actionToolbar.getComponent());
    popup.setMaximumListSizeLimit(PAGE_SIZE);
    popup.setListSizeIncreasing(PAGE_SIZE);

    showNavigationPopup(new GotoActionCallback<>() {
      @Override
      public void elementChosen(ChooseByNamePopup popup, Object element) {
        TaskManager taskManager = TaskManager.getManager(project);
        if (element instanceof TaskPsiElement) {
          Task task = ((TaskPsiElement)element).getTask();
          LocalTask localTask = taskManager.findTask(task.getId());
          if (localTask != null) {
            taskManager.activateTask(localTask, !shiftPressed.get());
          }
          else {
            showOpenTaskDialog(project, task);
          }
        }
        else if (element == CREATE_NEW_TASK_ACTION) {
          LocalTask localTask = taskManager.createLocalTask(CREATE_NEW_TASK_ACTION.getTaskName());
          showOpenTaskDialog(project, localTask);
        }
      }
    }, null, popup);
  }

  private static void showOpenTaskDialog(final Project project, final Task task) {
    JBPopup hint = DocumentationManager.getInstance(project).getDocInfoHint();
    if (hint != null) hint.cancel();
    ApplicationManager.getApplication().invokeLater(() -> new OpenTaskDialog(project, task).show());
  }

  private static class GotoTaskPopupModel extends SimpleChooseByNameModel implements DumbAware {
    private final ListCellRenderer myListCellRenderer;


    protected GotoTaskPopupModel(@NotNull Project project) {
      super(project, TaskBundle.message("enter.task.name"), null);
      myListCellRenderer = new TaskCellRenderer(project);
    }

    @Override
    public String[] getNames() {
      return ArrayUtilRt.EMPTY_STRING_ARRAY;
    }

    @Override
    protected Object[] getElementsByName(String name, String pattern) {
      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }

    @NotNull
    @Override
    public ListCellRenderer getListCellRenderer() {
      return myListCellRenderer;
    }

    @Override
    public String getElementName(@NotNull Object element) {
      if (element instanceof TaskPsiElement) {
        return TaskUtil.getTrimmedSummary(((TaskPsiElement)element).getTask());
      }
      else if (element == CREATE_NEW_TASK_ACTION) {
        return CREATE_NEW_TASK_ACTION.getActionText();
      }
      return null;
    }

    @Override
    public String getCheckBoxName() {
      return TaskBundle.message("label.include.closed.tasks");
    }

    @Override
    public void saveInitialCheckBoxState(final boolean state) {
      ((TaskManagerImpl)TaskManager.getManager(getProject())).getState().searchClosedTasks = state;
    }

    @Override
    public boolean loadInitialCheckBoxState() {
      return ((TaskManagerImpl)TaskManager.getManager(getProject())).getState().searchClosedTasks;
    }
  }

  /**
   * {@link ChooseByNameBase} and {@link ChooseByNamePopup} are not disposable (why?). So to correctly dispose alarm used in
   * {@link TaskItemProvider} and don't touch existing UI classes We have to extend popup and override {@link ChooseByNamePopup#close(boolean)}.
   */
  private static final class MyChooseByNamePopup extends ChooseByNamePopup {
    private MyChooseByNamePopup(@Nullable Project project,
                                @NotNull ChooseByNameModel model,
                                @NotNull ChooseByNameItemProvider provider,
                                @Nullable ChooseByNamePopup oldPopup,
                                @Nullable String predefinedText,
                                boolean mayRequestOpenInCurrentWindow, int initialIndex) {
      super(project, model, provider, oldPopup, predefinedText, mayRequestOpenInCurrentWindow, initialIndex);
    }

    @Override
    public void close(boolean isOk) {
      super.close(isOk);
      Disposer.dispose((TaskItemProvider)myProvider);
    }
  }

  /**
   * Mostly copied from {@link ChooseByNamePopup#createPopup(Project, ChooseByNameModel, ChooseByNameItemProvider, String, boolean, int)}.
   */
  private static MyChooseByNamePopup createPopup(Project project, ChooseByNameModel model, ChooseByNameItemProvider provider) {
    final ChooseByNamePopup oldPopup = project == null ? null : project.getUserData(ChooseByNamePopup.CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY);
    if (oldPopup != null) {
      oldPopup.close(false);
    }
    MyChooseByNamePopup newPopup = new MyChooseByNamePopup(project, model, provider, oldPopup, null, false, 0);

    if (project != null) {
      project.putUserData(ChooseByNamePopup.CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY, newPopup);
    }
    return newPopup;
  }

  public static class CreateNewTaskAction {
    private String taskName;

    public @Nls String getActionText() {
      return TaskBundle.message("create.new.task.0", taskName);
    }

    public void setTaskName(final String taskName) {
      this.taskName = taskName;
    }

    public String getTaskName() {
      return taskName;
    }
  }
}
