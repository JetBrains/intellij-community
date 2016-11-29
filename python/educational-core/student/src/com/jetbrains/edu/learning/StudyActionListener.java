package com.jetbrains.edu.learning;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.extensions.ExtensionPointName;

public interface StudyActionListener {
  ExtensionPointName<StudyActionListener> EP_NAME = ExtensionPointName.create("Edu.studyActionListener");

  void beforeCheck(AnActionEvent event);
}
