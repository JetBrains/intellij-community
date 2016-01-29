package com.jetbrains.edu.learning;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface StudyActionProvider {
  ExtensionPointName<StudyActionProvider> EP_NAME = ExtensionPointName.create("Edu.studyActionProvider");
  
  @Nullable
  DefaultActionGroup getActionGroup(@NotNull Project project);
  
}
