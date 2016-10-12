package com.jetbrains.edu.coursecreator.handlers;

import com.intellij.ide.TitledHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import org.jetbrains.annotations.NotNull;

public class CCLessonRenameHandler extends CCRenameHandler implements TitledHandler {
  @Override
  protected boolean isAvailable(String name) {
    return name.contains(EduNames.LESSON);
  }

  @Override
  protected void rename(@NotNull Project project, @NotNull Course course, @NotNull PsiDirectory directory) {
    Lesson lesson = course.getLesson(directory.getName());
    if (lesson != null) {
      processRename(lesson, EduNames.LESSON, project);
    }
  }

  @Override
  public String getActionTitle() {
    return "Rename lesson";
  }
}
