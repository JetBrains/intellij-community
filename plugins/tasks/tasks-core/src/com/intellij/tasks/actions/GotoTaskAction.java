package com.intellij.tasks.actions;

import com.intellij.ide.actions.GotoActionBase;
import com.intellij.ide.util.gotoByName.ChooseByNameBase;
import com.intellij.ide.util.gotoByName.ChooseByNameItemProvider;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.SimpleChooseByNameModel;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiManager;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.doc.TaskPsiElement;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Evgeny Zakrevsky
 */
public class GotoTaskAction extends GotoActionBase {
  public static final CreateNewTaskAction CREATE_NEW_TASK_ACTION = new CreateNewTaskAction();

  public GotoTaskAction() {
    getTemplatePresentation().setText("Goto Task...");
  }

  @Override
  protected void gotoActionPerformed(final AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return;
    final Ref<Boolean> shiftPressed = Ref.create(false);

    ChooseByNamePopup popup = ChooseByNamePopup.createPopup(project, new GotoTaskPopupModel(project), new ChooseByNameItemProvider() {
      @Override
      public List<String> filterNames(ChooseByNameBase base, String[] names, String pattern) {
        return ContainerUtil.emptyList();
      }

      @Override
      public void filterElements(ChooseByNameBase base,
                                 String pattern,
                                 boolean everywhere,
                                 Computable<Boolean> cancelled,
                                 Processor<Object> consumer) {
        Object[] elements = base.getModel().getElementsByName("", false, pattern);
        for (Object element : elements) {
          if (!consumer.process(element)) return;
        }
      }
    }, "", false, 0);
    popup.setShowListForEmptyPattern(true);
    popup.setSearchInAnyPlace(true);
    popup.setAdText("<html>Press SHIFT to merge with current context<br/>Pressing " + KeymapUtil
      .getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_QUICK_JAVADOC)) + " would show task description and comments</html>");
    popup.registerAction("shiftPressed", KeyStroke.getKeyStroke("shift pressed SHIFT"), new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        shiftPressed.set(true);
      }
    });
    popup.registerAction("shiftReleased", KeyStroke.getKeyStroke("released SHIFT"), new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        shiftPressed.set(false);
      }
    });

    showNavigationPopup(new GotoActionCallback<Object>() {
      @Override
      public void elementChosen(ChooseByNamePopup popup, Object element) {
        TaskManager taskManager = TaskManager.getManager(project);
        if (element instanceof TaskPsiElement) {
          Task task = ((TaskPsiElement)element).getTask();
          LocalTask localTask = taskManager.findTask(task.getId());
          if (localTask != null) {
            final boolean createChangelist =
              taskManager.isVcsEnabled() && !taskManager.getOpenChangelists(localTask).isEmpty();
            taskManager.activateTask(localTask, !shiftPressed.get(), createChangelist);
          }
          else {
            (new SimpleOpenTaskDialog(project, task)).show();

          }
        }
        else if (element == CREATE_NEW_TASK_ACTION) {
          popup.close(false);
          Task task = taskManager.createLocalTask(CREATE_NEW_TASK_ACTION.getTaskName());
          SimpleOpenTaskDialog simpleOpenTaskDialog = new SimpleOpenTaskDialog(project, task);
          simpleOpenTaskDialog.showAndGetOk();
        }
      }
    }, null, popup);

  }

  private static class GotoTaskPopupModel extends SimpleChooseByNameModel {
    private ListCellRenderer myListCellRenderer;
    private final Project myProject;


    protected GotoTaskPopupModel(@NotNull Project project) {
      super(project, "Enter task name:", null);
      myProject = project;
      myListCellRenderer = new TaskCellRenderer(project);
    }

    @Override
    public String[] getNames() {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    @Override
    protected Object[] getElementsByName(String name, String pattern) {
      List<Task> tasks = new ArrayList<Task>();
      tasks.addAll(TaskManager.getManager(myProject).getLocalTasks(pattern));
      tasks.addAll(ContainerUtil.filter(TaskManager.getManager(myProject).getIssues(pattern), new Condition<Task>() {
        @Override
        public boolean value(Task task) {
          return TaskManager.getManager(myProject).findTask(task.getId()) == null;
        }
      }));

      List<TaskPsiElement> taskPsiElements = ContainerUtil.map(tasks, new Function<Task, TaskPsiElement>() {
        @Override
        public TaskPsiElement fun(Task task) {
          return new TaskPsiElement(PsiManager.getInstance(myProject), task);
        }
      });
      TaskPsiElement[] result2 = new TaskPsiElement[taskPsiElements.size()];
      ArrayUtil.copy(taskPsiElements, result2, 0);

      final boolean foundTaskListEmpty = taskPsiElements.size() == 0;
      Object[] result = new Object[taskPsiElements.size() + 1 + (foundTaskListEmpty ? 0 : 1)];
      result[0] = CREATE_NEW_TASK_ACTION;
      CREATE_NEW_TASK_ACTION.setTaskName(pattern);
      if (!foundTaskListEmpty) {
        result[1] = ChooseByNameBase.NON_PREFIX_SEPARATOR;
      }
      ArrayUtil.copy(taskPsiElements, result, foundTaskListEmpty ? 1 : 2);
      return result;
    }

    @Override
    public ListCellRenderer getListCellRenderer() {
      return myListCellRenderer;
    }

    @Override
    public String getElementName(Object element) {
      if (element instanceof TaskPsiElement) {
        return TaskUtil.getTrimmedSummary(((TaskPsiElement)element).getTask());
      } else if (element == CREATE_NEW_TASK_ACTION) {
        return "Create New Task \"" + CREATE_NEW_TASK_ACTION.getActionText() + "\"...";
      }
      return null;
    }
  }

  public static class CreateNewTaskAction {
    private String taskName;

    public String getActionText() {
      return "Create New Task \'" + taskName + "\'";
    }

    public void setTaskName(final String taskName) {
      this.taskName = taskName;
    }

    public String getTaskName() {
      return taskName;
    }
  }
}
