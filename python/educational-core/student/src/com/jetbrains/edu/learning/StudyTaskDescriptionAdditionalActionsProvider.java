package com.jetbrains.edu.learning;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;

public interface StudyTaskDescriptionAdditionalActionsProvider {
  ExtensionPointName<StudyTaskDescriptionAdditionalActionsProvider> EP_NAME = ExtensionPointName.create("Edu.taskDescriptionAdditionalActionsProvider");

  AnAction[] getActions();
}
