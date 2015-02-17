package com.jetbrains.edu.coursecreator.actions;

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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformIcons;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.EduUtils;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.Lesson;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.coursecreator.CCLanguageManager;
import com.jetbrains.edu.coursecreator.CCProjectService;
import com.jetbrains.edu.coursecreator.CCUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CCCreateTask extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(CCCreateTask.class.getName());

  public CCCreateTask() {
    super("Task", "Create new Task", PlatformIcons.DIRECTORY_CLOSED_ICON);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final IdeView view = e.getData(LangDataKeys.IDE_VIEW);
    final Project project = e.getData(CommonDataKeys.PROJECT);

    if (project == null || view == null) {
      return;
    }
    final PsiDirectory directory = DirectoryChooserUtil.getOrChooseDirectory(view);

    if (directory == null) return;
    createTask(view, project, directory, true);
  }

  public static void createTask(final IdeView view, final Project project, final PsiDirectory lessonDir, boolean showDialog) {
    final CCProjectService service = CCProjectService.getInstance(project);
    final Course course = service.getCourse();
    final Lesson lesson = course.getLesson(lessonDir.getName());
    final int size = lesson.getTaskList().size();
    final String taskName;
    if (showDialog) {
      taskName = Messages.showInputDialog("Name:", "Task Name", null, EduNames.TASK + (size + 1), null);
    }
    else {
      taskName = EduNames.TASK + (size + 1);
    }

    if (taskName == null) {
      return;
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final PsiDirectory taskDirectory = DirectoryUtil.createSubdirectories(EduNames.TASK + (size + 1), lessonDir, "\\/");
        if (taskDirectory != null) {
          CCLanguageManager manager = CCUtils.getStudyLanguageManager(course);
          if (manager == null) {
            return;
          }

          EduUtils.markDirAsSourceRoot(taskDirectory.getVirtualFile(), project);
          final Task task = new Task(taskName);
          task.setIndex(size + 1);
          lesson.addTask(task);

          createFromTemplateAndOpen(taskDirectory, manager.getTestsTemplate(project), view);
          createFromTemplateAndOpen(taskDirectory, FileTemplateManager.getInstance(project).getInternalTemplate("task.html"), view);
          String defaultExtension = manager.getDefaultTaskFileExtension();
          if (defaultExtension != null) {
            FileTemplate taskFileTemplate = manager.getTaskFileTemplateForExtension(project, defaultExtension);
            createFromTemplateAndOpen(taskDirectory, taskFileTemplate, view);
            if (taskFileTemplate != null) {
              String taskFileName = FileUtil.getNameWithoutExtension(taskFileTemplate.getName());
              task.addTaskFile(taskFileName + "." + defaultExtension, size + 1);
            }
          }

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
              for (VirtualFile virtualFile : fileEditorManager.getOpenFiles()) {
                fileEditorManager.closeFile(virtualFile);
              }
            }
          });
        }
      }
    });
  }

  private static void createFromTemplateAndOpen(@NotNull final PsiDirectory taskDirectory,
                                                @Nullable final FileTemplate template,
                                                @Nullable IdeView view) {
    if (template == null) {
      return;
    }
    try {
      final PsiElement file = FileTemplateUtil.createFromTemplate(template, template.getName(), null, taskDirectory);
      if (view != null) {
        EditorHelper.openInEditor(file, false);
        view.selectElement(file);
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
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
    if (course != null && directory != null && course.getLesson(directory.getName()) == null) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }

    presentation.setVisible(true);
    presentation.setEnabled(true);
  }
}