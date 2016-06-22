package com.jetbrains.edu.learning.navigation;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.*;
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
      if (answerPlaceholder.getStatus() != StudyStatus.Failed) {
        continue;
      }
      navigateToAnswerPlaceholder(editor, answerPlaceholder);
      break;
    }
  }

  public static  void navigateToAnswerPlaceholder(@NotNull final Editor editor, @NotNull final AnswerPlaceholder answerPlaceholder) {
    if (editor.isDisposed()) {
      return;
    }
    editor.getCaretModel().moveToOffset(answerPlaceholder.getOffset());
  }


  public static  void navigateToFirstAnswerPlaceholder(@NotNull final Editor editor, @NotNull final TaskFile taskFile) {
    if (!taskFile.getAnswerPlaceholders().isEmpty()) {
      AnswerPlaceholder firstAnswerPlaceholder = StudyUtils.getFirst(taskFile.getAnswerPlaceholders());
      navigateToAnswerPlaceholder(editor, firstAnswerPlaceholder);
    }
  }

}
