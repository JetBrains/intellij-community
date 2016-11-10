package com.jetbrains.edu.learning;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.learning.courseFormat.Task;
import org.jetbrains.annotations.NotNull;

public interface StudySubtaskChangeListener {
  ExtensionPointName<StudySubtaskChangeListener> EP_NAME = ExtensionPointName.create("Edu.studySubtaskChangeListener");

  void subtaskChanged(@NotNull Project project, @NotNull Task task, int oldSubtaskNumber, int newSubtaskNumber);
}
