package com.intellij.tasks.actions;

import com.intellij.ide.actions.GotoActionBase;
import com.intellij.ide.util.gotoByName.ChooseByNameBase;
import com.intellij.ide.util.gotoByName.ChooseByNameItemProvider;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.SimpleChooseByNameModel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
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
import java.util.List;

/**
 * @author Evgeny Zakrevsky
 */
public class GotoTaskAction extends GotoActionBase {
  public static final CreateNewTaskAction CREATE_NEW_TASK_ACTION = new CreateNewTaskAction();

  public GotoTaskAction() {
    getTemplatePresentation().setText("Open Task...");
  }

  @Override
  protected void gotoActionPerformed(final AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return;
    perform(project);
  }

  void perform(final Project project) {
    myInAction = getClass();
    final Ref<Boolean> shiftPressed = Ref.create(false);

    final ChooseByNamePopup popup = ChooseByNamePopup.createPopup(project, new GotoTaskPopupModel(project), new ChooseByNameItemProvider() {
      @NotNull
      @Override
      public List<String> filterNames(@NotNull ChooseByNameBase base, @NotNull String[] names, @NotNull String pattern) {
        return ContainerUtil.emptyList();
      }

      @Override
      public boolean filterElements(@NotNull ChooseByNameBase base,
                                    @NotNull String pattern,
                                    boolean everywhere,
                                    @NotNull ProgressIndicator cancelled,
                                    @NotNull Processor<Object> consumer) {
        List<Task> cachedTasks = TaskSearchSupport.getLocalAndCachedTasks(TaskManager.getManager(project), pattern);
        List<TaskPsiElement> taskPsiElements = ContainerUtil.map(cachedTasks, new Function<Task, TaskPsiElement>() {
          @Override
          public TaskPsiElement fun(Task task) {
            return new TaskPsiElement(PsiManager.getInstance(project), task);
          }
        });

        CREATE_NEW_TASK_ACTION.setTaskName(pattern);
        cancelled.checkCanceled();
        if (!consumer.process(CREATE_NEW_TASK_ACTION)) return false;

        boolean cachedTasksFound = taskPsiElements.size() != 0;
        if (cachedTasksFound) {
          cancelled.checkCanceled();
          if (!consumer.process(ChooseByNameBase.NON_PREFIX_SEPARATOR)) return false;
        }

        for (Object element : taskPsiElements) {
          cancelled.checkCanceled();
          if (!consumer.process(element)) return false;
        }

        List<Task> tasks =
          TaskSearchSupport.getRepositoriesTasks(TaskManager.getManager(project), pattern, base.getMaximumListSizeLimit(), 0, true);
        tasks.removeAll(cachedTasks);
        taskPsiElements = ContainerUtil.map(tasks, new Function<Task, TaskPsiElement>() {
          @Override
          public TaskPsiElement fun(Task task) {
            return new TaskPsiElement(PsiManager.getInstance(project), task);
          }
        });

        if (!cachedTasksFound && taskPsiElements.size() != 0) {
          cancelled.checkCanceled();
          if (!consumer.process(ChooseByNameBase.NON_PREFIX_SEPARATOR)) return false;
        }

        for (Object element : taskPsiElements) {
          cancelled.checkCanceled();
          if (!consumer.process(element)) return false;
        }
        return true;
      }
    }, "", false, 0);
    popup.setShowListForEmptyPattern(true);
    popup.setSearchInAnyPlace(true);
    popup.setAdText("<html>Press SHIFT to merge with current context<br/>" +
                    "Pressing " +
                    KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_QUICK_JAVADOC)) +
                    " would show task description and comments</html>");
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

    final DefaultActionGroup group = new DefaultActionGroup(new ConfigureServersAction() {
      @Override
      protected void serversChanged() {
        popup.rebuildList(true);
      }
    });
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    actionToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    popup.setToolArea(actionToolbar.getComponent());

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
            popup.close(false);
            (new SimpleOpenTaskDialog(project, task)).show();
          }
        }
        else if (element == CREATE_NEW_TASK_ACTION) {
          popup.close(false);
          Task task = taskManager.createLocalTask(CREATE_NEW_TASK_ACTION.getTaskName());
          new SimpleOpenTaskDialog(project, task).show();
        }
      }
    }, null, popup);
  }

  private static class GotoTaskPopupModel extends SimpleChooseByNameModel {
    private ListCellRenderer myListCellRenderer;


    protected GotoTaskPopupModel(@NotNull Project project) {
      super(project, "Enter task name:", null);
      myListCellRenderer = new TaskCellRenderer(project);
    }

    @Override
    public String[] getNames() {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    @Override
    protected Object[] getElementsByName(String name, String pattern) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public ListCellRenderer getListCellRenderer() {
      return myListCellRenderer;
    }

    @Override
    public String getElementName(Object element) {
      if (element instanceof TaskPsiElement) {
        return TaskUtil.getTrimmedSummary(((TaskPsiElement)element).getTask());
      }
      else if (element == CREATE_NEW_TASK_ACTION) {
        return CREATE_NEW_TASK_ACTION.getActionText();
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
