package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.EduPluginConfigurator;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.Course;
import org.jetbrains.annotations.Nullable;

public class CCTestsTabTitleProvider implements EditorTabTitleProvider {
  @Nullable
  @Override
  public String getEditorTabTitle(Project project, VirtualFile file) {
    if (!CCUtils.isCourseCreator(project)) {
      return null;
    }
    if (!CCUtils.isTestsFile(project, file)) {
      return null;
    }
    Course course = StudyTaskManager.getInstance(project).getCourse();
    assert course != null;
    EduPluginConfigurator configurator = EduPluginConfigurator.INSTANCE.forLanguage(course.getLanguageById());
    if (configurator == null) {
      return null;
    }
    return configurator.getTestFileName();
  }
}