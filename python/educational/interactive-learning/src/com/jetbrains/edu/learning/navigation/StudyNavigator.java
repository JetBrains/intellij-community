package com.jetbrains.edu.learning.navigation;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.jetbrains.edu.courseFormat.*;
import com.jetbrains.edu.learning.StudyUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class StudyNavigator {
  private StudyNavigator() {

  }

  public static Task nextTask(@NotNull final Task task) {
    Lesson currentLesson = task.getLesson();
    List<Task> taskList = currentLesson.getTaskList();
    if (task.getIndex() + 1 < taskList.size()) {
      return taskList.get(task.getIndex() + 1);
    }
    Lesson nextLesson = nextLesson(currentLesson);
    if (nextLesson == null) {
      return null;
    }
    return StudyUtils.getFirst(nextLesson.getTaskList());
  }

  public static Task previousTask(@NotNull final Task task) {
    Lesson currentLesson = task.getLesson();
    if (task.getIndex() - 1 >= 0) {
      return currentLesson.getTaskList().get(task.getIndex() - 1);
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
    if (lesson.getIndex() + 1 >= lessons.size()) {
      return null;
    }
    return lessons.get(lesson.getIndex() + 1);
  }

  public static  Lesson previousLesson(@NotNull final Lesson lesson) {
    if (lesson.getIndex() - 1 < 0) {
      return null;
    }
    return lesson.getCourse().getLessons().get(lesson.getIndex() - 1);
  }

  public static  void navigateToFirstFailedTaskWindow(@NotNull final Editor editor, @NotNull final TaskFile taskFile) {
    for (AnswerPlaceholder answerPlaceholder : taskFile.getAnswerPlaceholders()) {
      if (answerPlaceholder.getStatus() != StudyStatus.Failed) {
        continue;
      }
      navigateToTaskWindow(editor, answerPlaceholder, taskFile);
      break;
    }
  }

  public static  void navigateToTaskWindow(@NotNull final Editor editor, @NotNull final AnswerPlaceholder answerPlaceholder,
                                   @NotNull final TaskFile taskFile) {
    if (!answerPlaceholder.isValid(editor.getDocument())) {
      return;
    }
    taskFile.setSelectedAnswerPlaceholder(answerPlaceholder);
    LogicalPosition taskWindowStart = new LogicalPosition(answerPlaceholder.getLine(), answerPlaceholder.getStart());
    editor.getCaretModel().moveToLogicalPosition(taskWindowStart);
  }


  public static  void navigateToFirstTaskWindow(@NotNull final Editor editor, @NotNull final TaskFile taskFile) {
    if (!taskFile.getAnswerPlaceholders().isEmpty()) {
      AnswerPlaceholder firstAnswerPlaceholder = StudyUtils.getFirst(taskFile.getAnswerPlaceholders());
      navigateToTaskWindow(editor, firstAnswerPlaceholder, taskFile);
    }
  }

}
