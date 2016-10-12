package com.jetbrains.edu.learning;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;

public interface StudyActionsProvider {
  ExtensionPointName<StudyActionsProvider> EP_NAME = ExtensionPointName.create("Edu.studyActionsProvider");

  AnAction[] getActions();
}
