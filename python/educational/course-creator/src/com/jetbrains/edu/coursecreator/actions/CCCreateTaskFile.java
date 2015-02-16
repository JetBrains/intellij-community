package com.jetbrains.edu.coursecreator.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeView;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.ide.util.EditorHelper;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.Lesson;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.coursecreator.CCLanguageManager;
import com.jetbrains.edu.coursecreator.CCProjectService;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.coursecreator.ui.CreateTaskFileDialog;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE;

public class CCCreateTaskFile extends DumbAwareAction {

  public CCCreateTaskFile() {
    super("Task File", "Create new Task File", AllIcons.FileTypes.Text);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
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
    CreateTaskFileDialog dialog = new CreateTaskFileDialog(project, generatedName, course);
    dialog.show();
    if (dialog.getExitCode() != OK_EXIT_CODE) {
      return;
    }
    final String taskFileName = dialog.getFileName();
    if (taskFileName == null) return;
    FileType type = dialog.getFileType();
    if (type == null) {
      return;
    }
    final CCLanguageManager CCLanguageManager = CCUtils.getStudyLanguageManager(course);
    if (CCLanguageManager == null) {
      return;
    }
    final String extension = type.getDefaultExtension();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final FileTemplate taskTemplate = CCLanguageManager.getTaskFileTemplateForExtension(project, extension);
        final String answerFileName = taskFileName + ".answer." + extension;
        try {
          if (taskTemplate == null) {
            VirtualFile file = taskDir.getVirtualFile().createChildData(this, answerFileName);
            ProjectView.getInstance(project).select(file, file, false);
            FileEditorManager.getInstance(project).openFile(file, true);
          }
          else {
            final PsiElement taskFile = FileTemplateUtil.createFromTemplate(taskTemplate, answerFileName, null, taskDir);
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                EditorHelper.openInEditor(taskFile, false);
                view.selectElement(taskFile);
              }
            });
          }
          task.addTaskFile(taskFileName + "." + extension, index);
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
    if (course != null && directory != null && !directory.getName().contains(EduNames.TASK)) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }
    presentation.setVisible(true);
    presentation.setEnabled(true);
  }
}
