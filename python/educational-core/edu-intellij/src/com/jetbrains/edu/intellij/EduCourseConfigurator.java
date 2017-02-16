package com.jetbrains.edu.intellij;

import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused") //used in other educational plugins that are stored in separate repository
public interface EduCourseConfigurator {
  String EP_NAME = "com.jetbrains.edu.intellij.courseConfigurator";
  LanguageExtension<EduCourseConfigurator> INSTANCE = new LanguageExtension<>(EP_NAME);

  default void configureModule(@NotNull final Project project) {}
}
