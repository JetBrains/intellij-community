package com.jetbrains.edu.coursecreator.actions;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.coursecreator.settings.CCSettings;
import com.jetbrains.edu.learning.EduPluginConfigurator;
import com.jetbrains.edu.learning.StudySubtaskUtils;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.courseFormat.tasks.TaskWithSubtasks;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public class CCNewSubtaskAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(CCNewSubtaskAction.class);
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
      task = replaceTaskWithSubtasks(task);
    }
    addSubtask((TaskWithSubtasks)task, project);
  }

  @NotNull
  private Task replaceTaskWithSubtasks(Task task) {
    final Lesson lesson = task.getLesson();
    final List<Task> list = lesson.getTaskList();
    final int i = list.indexOf(task);
    task = new TaskWithSubtasks(task);
    for (TaskFile taskFile : task.getTaskFiles().values()) {
      taskFile.setTask(task);
    }
    list.set(i, task);
    return task;
  }

  public static void addSubtask(@NotNull TaskWithSubtasks task, @NotNull Project project) {
    VirtualFile taskDir = task.getTaskDir(project);
    if (taskDir == null) {
      return;
    }
    createTestsForNewSubtask(project, task);
    int num = task.getLastSubtaskIndex() + 1;
    createTaskDescriptionFile(project, taskDir, num);
    StudySubtaskUtils.switchStep(project, task, num, false);
    task.setLastSubtaskIndex(num);
  }

  private static void createTestsForNewSubtask(Project project, Task task) {
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

  private static void createTaskDescriptionFile(Project project, VirtualFile taskDir, int index) {
    String taskDescriptionFileName = StudyUtils.getTaskDescriptionFileName(CCSettings.getInstance().useHtmlAsDefaultTaskFormat());
    FileTemplate taskTextTemplate = FileTemplateManager.getInstance(project).getInternalTemplate(taskDescriptionFileName);
    PsiDirectory taskPsiDir = PsiManager.getInstance(project).findDirectory(taskDir);
    if (taskTextTemplate != null && taskPsiDir != null) {
      String nextTaskTextName = FileUtil.getNameWithoutExtension(taskDescriptionFileName) +
                                EduNames.SUBTASK_MARKER +
                                index + "." +
                                FileUtilRt.getExtension(taskDescriptionFileName);
      try {
        FileTemplateUtil.createFromTemplate(taskTextTemplate, nextTaskTextName, null, taskPsiDir);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
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