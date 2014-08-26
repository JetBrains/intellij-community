package org.jetbrains.plugins.coursecreator.actions;

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
import icons.PythonPsiApiIcons;
import org.jetbrains.plugins.coursecreator.CCProjectService;
import org.jetbrains.plugins.coursecreator.format.Course;
import org.jetbrains.plugins.coursecreator.format.Lesson;
import org.jetbrains.plugins.coursecreator.format.Task;

public class CreateTaskFile extends DumbAwareAction {

  public CreateTaskFile() {
    super("Task File", "Create new Task File", PythonPsiApiIcons.PythonFile);
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
          final FileTemplate taskTemplate = FileTemplateManager.getInstance().getInternalTemplate("task.py");
          try {
            final PsiElement taskPyFile = FileTemplateUtil.createFromTemplate(taskTemplate, taskFileName + ".py", null, taskDir);
            task.addTaskFile(taskPyFile.getContainingFile().getName(), index);
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
  public void update(AnActionEvent event) {
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
