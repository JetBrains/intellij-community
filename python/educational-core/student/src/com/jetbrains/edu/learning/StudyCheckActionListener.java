package com.jetbrains.edu.learning;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.extensions.ExtensionPointName;

public interface StudyCheckActionListener {
  ExtensionPointName<StudyCheckActionListener> EP_NAME = ExtensionPointName.create("Edu.studyCheckActionListener");

  void beforeCheck(AnActionEvent event);
}
