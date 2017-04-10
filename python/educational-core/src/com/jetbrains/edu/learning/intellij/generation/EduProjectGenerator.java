package com.jetbrains.edu.learning.intellij.generation;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import org.jetbrains.annotations.NotNull;

public class EduProjectGenerator extends StudyProjectGenerator {
  private static final Logger LOG = Logger.getInstance(EduProjectGenerator.class);

  @Override
  public void generateProject(@NotNull Project project, @NotNull VirtualFile baseDir) {
    final Course course = getCourse(project);
    if (course == null) {
      LOG.warn("Failed to get course");
      return;
    }
    StudyTaskManager.getInstance(project).setCourse(course);
  }
}
