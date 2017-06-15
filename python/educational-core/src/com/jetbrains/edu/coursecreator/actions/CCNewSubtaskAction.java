package com.jetbrains.edu.coursecreator.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.learning.EduPluginConfigurator;
import com.jetbrains.edu.learning.StudySubtaskUtils;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.courseFormat.tasks.TaskWithSubtasks;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.jetbrains.edu.coursecreator.CCUtils.renameFiles;


public class CCNewSubtaskAction extends DumbAwareAction {
  public static final String NEW_SUBTASK = "Add Subtask";

  public CCNewSubtaskAction() {
    super(NEW_SUBTASK);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (virtualFile == null || project == null || editor == null) {
      return;
    }
    Task task = StudyUtils.getTaskForFile(project, virtualFile);
    if (task == null) return;
    if (!(task instanceof TaskWithSubtasks)) {
      task = convertToTaskWithSubtasks(task, project);
    }
    addSubtask((TaskWithSubtasks)task, project);
  }

  @NotNull
  private static Task convertToTaskWithSubtasks(Task task, Project project) {
    final Lesson lesson = task.getLesson();
    final List<Task> list = lesson.getTaskList();
    final int i = list.indexOf(task);
    task = new TaskWithSubtasks(task);
    for (TaskFile taskFile : task.getTaskFiles().values()) {
      taskFile.setTask(task);
    }
    list.set(i, task);
    final VirtualFile taskDir = task.getTaskDir(project);
    renameFiles(taskDir, project, -1);
    return task;
  }

  public static void addSubtask(@NotNull TaskWithSubtasks task, @NotNull Project project) {
    VirtualFile taskDir = task.getTaskDir(project);
    if (taskDir == null) {
      return;
    }
    createTestsForNewSubtask(project, task);
    int num = task.getLastSubtaskIndex() + 1;
    StudySubtaskUtils.switchStep(project, task, num, false);
    task.setLastSubtaskIndex(num);
  }

  private static void createTestsForNewSubtask(Project project, TaskWithSubtasks task) {
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return;
    }
    EduPluginConfigurator configurator = EduPluginConfigurator.INSTANCE.forLanguage(course.getLanguageById());
    if (configurator == null) {
      return;
    }
    configurator.createTestsForNewSubtask(project, task);
  }

  @Override
  public void update(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(false);
    VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (virtualFile == null || project == null) {
      return;
    }
    if (!CCUtils.isCourseCreator(project)) {
      return;
    }
    if (StudyUtils.getTaskForFile(project, virtualFile) != null || StudyUtils.getTask(project, virtualFile) != null) {
      presentation.setEnabledAndVisible(true);
    }
  }
}