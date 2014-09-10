package org.jetbrains.plugins.coursecreator.actions;

import com.intellij.ide.IdeView;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.ide.util.DirectoryUtil;
import com.intellij.ide.util.EditorHelper;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.coursecreator.CCProjectService;
import org.jetbrains.plugins.coursecreator.format.Course;
import org.jetbrains.plugins.coursecreator.format.Lesson;
import org.jetbrains.plugins.coursecreator.format.Task;

public class CreateTask extends DumbAwareAction {
  public CreateTask() {
    super("Task", "Create new Task", PlatformIcons.DIRECTORY_CLOSED_ICON);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final IdeView view = e.getData(LangDataKeys.IDE_VIEW);
    final Project project = e.getData(CommonDataKeys.PROJECT);

    if (view == null || project == null) {
      return;
    }
    final PsiDirectory directory = DirectoryChooserUtil.getOrChooseDirectory(view);

    if (directory == null) return;
    final CCProjectService service = CCProjectService.getInstance(project);
    final Course course = service.getCourse();
    final Lesson lesson = course.getLesson(directory.getName());
    final int size = lesson.getTaskList().size();

    final String taskName = Messages.showInputDialog("Name:", "Task Name", null, "task" + (size + 1), null);
    if (taskName == null) return;

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final PsiDirectory taskDirectory = DirectoryUtil.createSubdirectories("task" + (size + 1), directory, "\\/");
        if (taskDirectory != null) {
          final FileTemplate template = FileTemplateManager.getInstance().getInternalTemplate("task.html");
          final FileTemplate testsTemplate = FileTemplateManager.getInstance().getInternalTemplate("tests");
          final FileTemplate taskTemplate = FileTemplateManager.getInstance().getInternalTemplate("task.answer");
          try {
            final PsiElement taskFile = FileTemplateUtil.createFromTemplate(template, "task.html", null, taskDirectory);
            final PsiElement testsFile = FileTemplateUtil.createFromTemplate(testsTemplate, "tests.py", null, taskDirectory);
            final PsiElement taskPyFile = FileTemplateUtil.createFromTemplate(taskTemplate, "file1", null, taskDirectory);

            final Task task = new Task(taskName);
            task.addTaskFile("file1.py", size + 1);
            task.setIndex(size + 1);
            lesson.addTask(task, taskDirectory);
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                EditorHelper.openInEditor(testsFile, false);
                EditorHelper.openInEditor(taskPyFile, false);
                view.selectElement(taskFile);
              }
            });
          }
          catch (Exception ignored) {
          }


        }
      }
    });
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    final Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }

    final IdeView view = event.getData(LangDataKeys.IDE_VIEW);
    if (view == null) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }

    final PsiDirectory[] directories = view.getDirectories();
    if (directories.length == 0) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }
    final PsiDirectory directory = DirectoryChooserUtil.getOrChooseDirectory(view);
    final CCProjectService service = CCProjectService.getInstance(project);
    final Course course = service.getCourse();
    if (course != null && directory != null && course.getLesson(directory.getName()) == null) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }

    presentation.setVisible(true);
    presentation.setEnabled(true);

  }
}