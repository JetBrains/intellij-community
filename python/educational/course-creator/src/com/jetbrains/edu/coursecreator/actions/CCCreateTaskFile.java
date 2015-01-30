package com.jetbrains.edu.coursecreator.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeView;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.util.DirectoryChooserUtil;
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
import com.jetbrains.edu.coursecreator.CCProjectService;
import com.jetbrains.edu.coursecreator.format.Course;
import com.jetbrains.edu.coursecreator.format.Lesson;
import com.jetbrains.edu.coursecreator.format.Task;
import org.jetbrains.annotations.NotNull;

public class CCCreateTaskFile extends DumbAwareAction {

  public CCCreateTaskFile() {
    super("Task File", "Create new Task File", AllIcons.FileTypes.Text);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final IdeView view = e.getData(LangDataKeys.IDE_VIEW);
    final Project project = e.getData(CommonDataKeys.PROJECT);

    if (view == null || project == null) {
      return;
    }
    final PsiDirectory taskDir = DirectoryChooserUtil.getOrChooseDirectory(view);
    if (taskDir == null) return;
    PsiDirectory lessonDir = taskDir.getParent();
    if (lessonDir == null) {
      return;
    }
    final CCProjectService service = CCProjectService.getInstance(project);
    final Course course = service.getCourse();
    final Lesson lesson = course.getLesson(lessonDir.getName());
    final Task task = lesson.getTask(taskDir.getName());

    final int index = task.getTaskFiles().size() + 1;
    String generatedName = "file" + index;
    final String taskFileName = Messages.showInputDialog("Name:", "Task File Name", null, generatedName, null);
    if (taskFileName == null) return;

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
          final FileTemplate taskTemplate = FileTemplateManager.getInstance(project).getInternalTemplate("task.answer");
          try {
            final PsiElement taskPyFile = FileTemplateUtil.createFromTemplate(taskTemplate, taskFileName, null, taskDir);
            task.addTaskFile(taskFileName + ".py", index);
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                EditorHelper.openInEditor(taskPyFile, false);
                view.selectElement(taskPyFile);
              }
            });
          }
          catch (Exception ignored) {
          }
      }
    });
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    if (!CCProjectService.setCCActionAvailable(event)) {
      return;
    }
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
    if (course != null && directory != null && !directory.getName().contains("task")) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }
    presentation.setVisible(true);
    presentation.setEnabled(true);
  }
}
