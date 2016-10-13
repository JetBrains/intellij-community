package com.jetbrains.tmp.learning;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;

public interface StudyActionsProvider {
  ExtensionPointName<StudyActionsProvider> EP_NAME = ExtensionPointName.create("SCore.studyActionsProvider");

  AnAction[] getActions();
}
