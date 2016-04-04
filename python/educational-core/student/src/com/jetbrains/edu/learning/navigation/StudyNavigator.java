package com.jetbrains.edu.learning.navigation;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.courseFormat.*;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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

  public static  Lesson nextLesson(@NotNull final Lesson lesson) {
    List<Lesson> lessons = lesson.getCourse().getLessons();
    int nextLessonIndex = lesson.getIndex();
    if (nextLessonIndex >= lessons.size()) {
      return null;
    }
    final Lesson nextLesson = lessons.get(nextLessonIndex);
    if (EduNames.PYCHARM_ADDITIONAL.equals(nextLesson.getName()))
      return null;
    return nextLesson;
  }

  public static  Lesson previousLesson(@NotNull final Lesson lesson) {
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
      final StudyStatus status = StudyTaskManager.getInstance(project).getStatus(answerPlaceholder);
      if (status != StudyStatus.Failed) {
        continue;
      }
      navigateToAnswerPlaceholder(editor, answerPlaceholder, taskFile);
      break;
    }
  }

  public static  void navigateToAnswerPlaceholder(@NotNull final Editor editor, @NotNull final AnswerPlaceholder answerPlaceholder,
                                                  @NotNull final TaskFile taskFile) {
    if (!answerPlaceholder.isValid(editor.getDocument())) {
      return;
    }
    LogicalPosition placeholderStart = new LogicalPosition(answerPlaceholder.getLine(), answerPlaceholder.getStart());
    editor.getCaretModel().moveToLogicalPosition(placeholderStart);
  }


  public static  void navigateToFirstAnswerPlaceholder(@NotNull final Editor editor, @NotNull final TaskFile taskFile) {
    if (!taskFile.getAnswerPlaceholders().isEmpty()) {
      AnswerPlaceholder firstAnswerPlaceholder = StudyUtils.getFirst(taskFile.getAnswerPlaceholders());
      navigateToAnswerPlaceholder(editor, firstAnswerPlaceholder, taskFile);
    }
  }

}
