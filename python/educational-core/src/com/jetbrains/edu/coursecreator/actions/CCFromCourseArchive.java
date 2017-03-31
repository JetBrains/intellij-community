package com.jetbrains.edu.coursecreator.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduDocumentListener;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.courseGeneration.StudyGenerator;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class CCFromCourseArchive extends DumbAwareAction {
  public CCFromCourseArchive() {
    super("Unpack Course Archive", "Unpack Course Archive", AllIcons.FileTypes.Archive);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    unpackCourseArchive(project);
  }

  private static void unpackCourseArchive(final Project project) {
    FileChooserDescriptor descriptor =
      new FileChooserDescriptor(true, true, true, true, true, false);

    final VirtualFile virtualFile = FileChooser.chooseFile(descriptor, project, null);
    if (virtualFile == null) {
      return;
    }
    final String basePath = project.getBasePath();
    if (basePath == null) return;

    Course course = StudyProjectGenerator.getCourse(virtualFile.getPath());
    if (course == null) {
      Messages.showErrorDialog("This course is incompatible with current version", "Failed to Unpack Course");
      return;
    }
    generateFromStudentCourse(project, course);
  }

  public static void generateFromStudentCourse(Project project, Course course) {
    StudyTaskManager.getInstance(project).setCourse(course);
    course.setCourseMode(CCUtils.COURSE_MODE);
    final VirtualFile baseDir = project.getBaseDir();
    final Application application = ApplicationManager.getApplication();

    application.invokeAndWait(() -> application.runWriteAction(() -> {
      final VirtualFile[] children = baseDir.getChildren();
      for (VirtualFile child : children) {
        StudyUtils.deleteFile(child);
      }
      StudyGenerator.createCourse(course, baseDir);
    }));
    baseDir.refresh(false, true);

    int index = 1;
    int taskIndex = 1;
    for (Lesson lesson : course.getLessons()) {
      final VirtualFile lessonDir = project.getBaseDir().findChild(EduNames.LESSON + String.valueOf(index));
      lesson.setIndex(index);
      if (lessonDir == null) continue;
      for (Task task : lesson.getTaskList()) {
        final VirtualFile taskDir = lessonDir.findChild(EduNames.TASK + String.valueOf(taskIndex));
        task.setIndex(taskIndex);
        task.setLesson(lesson);
        if (taskDir == null) continue;
        for (final Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
          application.invokeAndWait(() -> application.runWriteAction(() -> createAnswerFile(project, taskDir, entry)));
        }
        taskIndex += 1;
      }
      index += 1;
      taskIndex = 1;
    }
    course.initCourse(true);
    application.invokeAndWait(() -> StudyUtils.registerStudyToolWindow(course, project));
    synchronize(project);
  }

  public static void createAnswerFile(@NotNull final Project project,
                                      @NotNull final VirtualFile userFileDir,
                                      @NotNull final Map.Entry<String, TaskFile> taskFileEntry) {
    final String name = taskFileEntry.getKey();
    final TaskFile taskFile = taskFileEntry.getValue();
    VirtualFile file = userFileDir.findFileByRelativePath(name);
    assert file != null;
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) return;

    CommandProcessor.getInstance().executeCommand(project,() -> ApplicationManager.getApplication().runWriteAction(
      () -> document.replaceString(0, document.getTextLength(), document.getCharsSequence())),
                                                  "Create answer document", "Create answer document");
    EduDocumentListener listener = new EduDocumentListener(taskFile, false);
    document.addDocumentListener(listener);
    taskFile.sortAnswerPlaceholders();

    for (AnswerPlaceholder placeholder : taskFile.getActivePlaceholders()) {
      replaceAnswerPlaceholder(document, placeholder);
    }
    for (AnswerPlaceholder placeholder : taskFile.getAnswerPlaceholders()) {
      placeholder.setUseLength(false);
    }

    CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(
      () -> FileDocumentManager.getInstance().saveDocument(document)),"Create answer document", "Create answer document");
    document.removeDocumentListener(listener);
  }

  private static void replaceAnswerPlaceholder(@NotNull final Document document,
                                               @NotNull final AnswerPlaceholder placeholder) {
    final int offset = placeholder.getOffset();
    final String text = document.getText(TextRange.create(offset, offset + placeholder.getRealLength()));
    placeholder.setTaskText(text);
    placeholder.init();
    String replacementText = placeholder.getPossibleAnswer();

    CommandProcessor.getInstance().runUndoTransparentAction(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      document.replaceString(offset, offset + placeholder.getRealLength(), replacementText);
      FileDocumentManager.getInstance().saveDocument(document);
    }));
  }

  private static void synchronize(@NotNull final Project project) {
    VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
    ProjectView.getInstance(project).refresh();
  }
}