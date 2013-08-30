package com.intellij.tasks.actions;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.ide.actions.GotoActionBase;
import com.intellij.ide.util.gotoByName.ChooseByNameBase;
import com.intellij.ide.util.gotoByName.ChooseByNameItemProvider;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.SimpleChooseByNameModel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiManager;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.doc.TaskPsiElement;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * @author Evgeny Zakrevsky
 */
public class GotoTaskAction extends GotoActionBase implements DumbAware {
  public static final CreateNewTaskAction CREATE_NEW_TASK_ACTION = new CreateNewTaskAction();
  public static final String ID = "tasks.goto";

  public GotoTaskAction() {
    getTemplatePresentation().setText("Open Task...");
    getTemplatePresentation().setIcon(IconUtil.getAddIcon());
  }

  @Override
  protected void gotoActionPerformed(final AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return;
    perform(project);
  }

  void perform(final Project project) {
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

        CREATE_NEW_TASK_ACTION.setTaskName(pattern);
        if (!consumer.process(CREATE_NEW_TASK_ACTION)) return false;

        List<Task> cachedAndLocalTasks = TaskSearchSupport.getLocalAndCachedTasks(TaskManager.getManager(project), pattern, everywhere);
        boolean cachedTasksFound = !cachedAndLocalTasks.isEmpty();
        if (!processTasks(cachedAndLocalTasks, consumer, cachedTasksFound, cancelled, PsiManager.getInstance(project))) return false;

        List<Task> tasks = TaskSearchSupport
          .getRepositoriesTasks(TaskManager.getManager(project), pattern, base.getMaximumListSizeLimit(), 0, true, everywhere, cancelled);
        tasks.removeAll(cachedAndLocalTasks);

        return processTasks(tasks, consumer, cachedTasksFound, cancelled, PsiManager.getInstance(project));
      }
    }, null, false, 0);

    popup.setShowListForEmptyPattern(true);
    popup.setSearchInAnyPlace(true);
    popup.setFixLostTyping(false);
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
    actionToolbar.updateActionsImmediately();
    actionToolbar.getComponent().setFocusable(false);
    actionToolbar.getComponent().setBorder(null);
    popup.setToolArea(actionToolbar.getComponent());
    popup.setMaximumListSizeLimit(10);
    popup.setListSizeIncreasing(10);

    showNavigationPopup(new GotoActionCallback<Object>() {
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

  private static boolean processTasks(List<Task> tasks,
                                      Processor<Object> consumer,
                                      boolean cachedTasksFound,
                                      ProgressIndicator cancelled,
                                      PsiManager psiManager) {
    if (!cachedTasksFound && !tasks.isEmpty() && !consumer.process(ChooseByNameBase.NON_PREFIX_SEPARATOR)) return false;

    for (Task task : tasks) {
      cancelled.checkCanceled();
      if (!consumer.process(new TaskPsiElement(psiManager, task))) return false;
    }
    return true;
  }

  private static void showOpenTaskDialog(final Project project, final Task task) {
    JBPopup hint = DocumentationManager.getInstance(project).getDocInfoHint();
    if (hint != null) hint.cancel();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        new OpenTaskDialog(project, task).show();
      }
    });
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

    @Override
    public String getCheckBoxName() {
      return "Include closed tasks";
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
