package com.jetbrains.edu.coursecreator.handlers;

import com.intellij.ide.TitledHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.Task;
import org.jetbrains.annotations.NotNull;

public class CCTaskRenameHandler extends CCRenameHandler implements TitledHandler {
  @Override
  protected boolean isAvailable(String name) {
    return name.contains(EduNames.TASK);
  }

  @Override
  protected void rename(@NotNull Project project, @NotNull Course course, @NotNull PsiDirectory directory) {
    PsiDirectory lessonDir = directory.getParent();
    if (lessonDir == null || !lessonDir.getName().contains(EduNames.LESSON)) {
      return;
    }
    Lesson lesson = course.getLesson(lessonDir.getName());
    if (lesson == null) {
      return;
    }
    String directoryName = directory.getName();
    Task task = lesson.getTask(directoryName);
    if (task != null) {
      processRename(task, EduNames.TASK, project);
    }
  }

  @Override
  public String getActionTitle() {
    return "Rename task";
  }
}
