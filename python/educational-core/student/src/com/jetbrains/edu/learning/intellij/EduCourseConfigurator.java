package com.jetbrains.edu.learning.intellij;

import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import com.jetbrains.edu.learning.courseFormat.CourseInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("unused") //used in other educational plugins that are stored in separate repository
public interface EduCourseConfigurator {
  String EP_NAME = "Edu.courseConfigurator";
  LanguageExtension<EduCourseConfigurator> INSTANCE = new LanguageExtension<>(EP_NAME);

  default void configureModule(@NotNull Module module) {
  }

  default void createCourseFromCourseInfo(@NotNull ModifiableModuleModel moduleModel,
                                          @NotNull Project project,
                                          @NotNull StudyProjectGenerator generator,
                                          @NotNull CourseInfo selectedCourse,
                                          @Nullable String moduleDir) {
  }

  default void createCourseModuleContent(@NotNull ModifiableModuleModel moduleModel,
                                         @NotNull Project project,
                                         @NotNull Course course,
                                         @Nullable String moduleDir) {
  }
}
