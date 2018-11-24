package com.jetbrains.edu.learning.navigation;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static com.jetbrains.edu.learning.actions.StudyTaskNavigationAction.updateProjectView;

public class StudyNavigator {
  private StudyNavigator() {

  }

  public static Task nextTask(@NotNull final Task task) {
    Lesson currentLesson = task.getLesson();
    List<Task> taskList = currentLesson.getTaskList();
    if (task.getIndex() < taskList.size()) {
      return taskList.get(task.getIndex());
    }
    Lesson nextLesson = nextLesson(currentLesson);
    if (nextLesson == null) {
      return null;
    }
    return StudyUtils.getFirst(nextLesson.getTaskList());
  }

  public static Task previousTask(@NotNull final Task task) {
    Lesson currentLesson = task.getLesson();
    int prevTaskIndex = task.getIndex() - 2;
    if (prevTaskIndex >= 0) {
      return currentLesson.getTaskList().get(prevTaskIndex);
    }
    Lesson prevLesson = previousLesson(currentLesson);
    if (prevLesson == null) {
      return null;
    }
    //getting last task in previous lesson
    return prevLesson.getTaskList().get(prevLesson.getTaskList().size() - 1);
  }

  public static Lesson nextLesson(@NotNull final Lesson lesson) {
    List<Lesson> lessons = lesson.getCourse().getLessons();
    int nextLessonIndex = lesson.getIndex();
    if (nextLessonIndex >= lessons.size()) {
      return null;
    }
    final Lesson nextLesson = lessons.get(nextLessonIndex);
    if (EduNames.PYCHARM_ADDITIONAL.equals(nextLesson.getName())) {
      return null;
    }
    return nextLesson;
  }

  public static Lesson previousLesson(@NotNull final Lesson lesson) {
    int prevLessonIndex = lesson.getIndex() - 2;
    if (prevLessonIndex < 0) {
      return null;
    }
    return lesson.getCourse().getLessons().get(prevLessonIndex);
  }

  public static void navigateToFirstFailedAnswerPlaceholder(@NotNull final Editor editor, @NotNull final TaskFile taskFile) {
    final Project project = editor.getProject();
    if (project == null) return;
    for (AnswerPlaceholder answerPlaceholder : taskFile.getAnswerPlaceholders()) {
      if (answerPlaceholder.getStatus() != StudyStatus.Failed) {
        continue;
      }
      navigateToAnswerPlaceholder(editor, answerPlaceholder);
      break;
    }
  }

  public static void navigateToAnswerPlaceholder(@NotNull final Editor editor, @NotNull final AnswerPlaceholder answerPlaceholder) {
    if (editor.isDisposed()) {
      return;
    }
    int startOffset = answerPlaceholder.getOffset();
    editor.getCaretModel().moveToOffset(startOffset);
    editor.getSelectionModel().setSelection(startOffset, startOffset + answerPlaceholder.getRealLength());
  }


  public static void navigateToFirstAnswerPlaceholder(@NotNull final Editor editor, @NotNull final TaskFile taskFile) {
    if (!taskFile.getAnswerPlaceholders().isEmpty()) {
      AnswerPlaceholder firstAnswerPlaceholder = StudyUtils.getFirst(taskFile.getAnswerPlaceholders());
      if (firstAnswerPlaceholder == null) return;
      navigateToAnswerPlaceholder(editor, firstAnswerPlaceholder);
    }
  }

  @Nullable
  public static VirtualFile getFileToActivate(@NotNull Project project, Map<String, TaskFile> nextTaskFiles, VirtualFile taskDir) {
    VirtualFile shouldBeActive = null;
    for (Map.Entry<String, TaskFile> entry : nextTaskFiles.entrySet()) {
      String name = entry.getKey();
      TaskFile taskFile = entry.getValue();
      VirtualFile srcDir = taskDir.findChild(EduNames.SRC);
      VirtualFile vf = srcDir == null ? taskDir.findChild(name) : srcDir.findChild(name);
      if (vf != null) {
        if (shouldBeActive != null) {
          FileEditorManager.getInstance(project).openFile(vf, true);
        }
        if (shouldBeActive == null && !taskFile.getAnswerPlaceholders().isEmpty()) {
          shouldBeActive = vf;
        }
      }
    }
    return shouldBeActive != null ? shouldBeActive : getFirstTaskFile(taskDir, project);
  }

  @Nullable
  private static VirtualFile getFirstTaskFile(@NotNull final VirtualFile taskDir, @NotNull final Project project) {
    for (VirtualFile virtualFile : taskDir.getChildren()) {
      if (StudyUtils.getTaskFile(project, virtualFile) != null) {
        return virtualFile;
      }
    }
    return null;
  }

  public static void navigateToTask(@NotNull final Project project, @NotNull final String lessonName, @NotNull final String taskName) {
    final Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return;
    }
    final Lesson lesson = course.getLesson(lessonName);
    if (lesson == null) {
      return;
    }
    final Task task = lesson.getTask(taskName);
    if (task == null) {
      return;
    }
    ApplicationManager.getApplication().invokeLater(() -> {
      for (VirtualFile file : FileEditorManager.getInstance(project).getOpenFiles()) {
        FileEditorManager.getInstance(project).closeFile(file);
      }
      int nextTaskIndex = task.getIndex();
      int lessonIndex = task.getLesson().getIndex();
      Map<String, TaskFile> nextTaskFiles = task.getTaskFiles();
      VirtualFile projectDir = project.getBaseDir();
      String lessonDirName = EduNames.LESSON + String.valueOf(lessonIndex);
      if (projectDir == null) {
        return;
      }
      VirtualFile lessonDir = projectDir.findChild(lessonDirName);
      if (lessonDir == null) {
        return;
      }
      String taskDirName = EduNames.TASK + String.valueOf(nextTaskIndex);
      VirtualFile taskDir = lessonDir.findChild(taskDirName);
      if (taskDir == null) {
        return;
      }
      if (nextTaskFiles.isEmpty()) {
        ProjectView.getInstance(project).select(taskDir, taskDir, false);
      }
      VirtualFile toActivate = getFileToActivate(project, nextTaskFiles, taskDir);

      updateProjectView(project, toActivate);
    });
  }
}
