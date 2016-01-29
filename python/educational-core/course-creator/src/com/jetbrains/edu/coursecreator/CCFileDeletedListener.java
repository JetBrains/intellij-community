package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.util.Function;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.courseFormat.*;
import com.jetbrains.edu.coursecreator.actions.CCRunTestsAction;
import org.jetbrains.annotations.NotNull;

class CCFileDeletedListener extends VirtualFileAdapter {

  private final Project myProject;

  CCFileDeletedListener(@NotNull final Project project) {
    myProject = project;
  }

  @Override
  public void fileDeleted(@NotNull VirtualFileEvent event) {
    if (myProject.isDisposed() || !myProject.isOpen()) {
      return;
    }
    VirtualFile removedFile = event.getFile();
    final TaskFile taskFile = CCProjectService.getInstance(myProject).getTaskFile(removedFile);
    if (taskFile != null) {
      deleteAnswerFile(removedFile, taskFile);
      return;
    }
    Course course = CCProjectService.getInstance(myProject).getCourse();
    if (course == null) {
      return;
    }
    if (removedFile.getName().contains(EduNames.TASK)) {
      deleteTask(course, removedFile, myProject);
    }
    if (removedFile.getName().contains(EduNames.LESSON)) {
      deleteLesson(course, removedFile);
    }
  }

  private void deleteLesson(@NotNull final Course course, @NotNull final VirtualFile removedLessonFile) {
    Lesson removedLesson = course.getLesson(removedLessonFile.getName());
    if (removedLesson == null) {
      return;
    }
    VirtualFile courseDir = myProject.getBaseDir();
    CCUtils.updateHigherElements(courseDir.getChildren(), new Function<VirtualFile, StudyItem>() {
      @Override
      public StudyItem fun(VirtualFile file) {
        return course.getLesson(file.getName());
      }
    }, removedLesson.getIndex(), EduNames.LESSON, -1);
    course.getLessons().remove(removedLesson);
  }

  private static void deleteTask(@NotNull final Course course, @NotNull final VirtualFile removedTask, @NotNull final Project project) {
    VirtualFile lessonDir = removedTask.getParent();
    if (lessonDir == null || !lessonDir.getName().contains(EduNames.LESSON)) {
      return;
    }
    final Lesson lesson = course.getLesson(lessonDir.getName());
    if (lesson == null) {
      return;
    }
    Task task = lesson.getTask(removedTask.getName());
    if (task == null) {
      return;
    }
    CCUtils.updateHigherElements(lessonDir.getChildren(), new Function<VirtualFile, StudyItem>() {
      @Override
      public StudyItem fun(VirtualFile file) {
        return lesson.getTask(file.getName());
      }
    }, task.getIndex(), EduNames.TASK, -1);
    lesson.getTaskList().remove(task);
  }

  private void deleteAnswerFile(@NotNull final VirtualFile removedAnswerFile, TaskFile taskFile) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        VirtualFile taskDir = removedAnswerFile.getParent();
        if (taskDir != null) {
          CCRunTestsAction.clearTestEnvironment(taskDir, myProject);
        }
      }
    });
    String name = CCProjectService.getRealTaskFileName(removedAnswerFile.getName());
    Task task = taskFile.getTask();
    if (task == null) {
      return;
    }
    task.getTaskFiles().remove(name);
  }
}
