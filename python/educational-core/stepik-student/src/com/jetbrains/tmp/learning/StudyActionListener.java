package com.jetbrains.tmp.learning;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.extensions.ExtensionPointName;

public interface StudyActionListener {
  ExtensionPointName<StudyActionListener> EP_NAME = ExtensionPointName.create("SCore.studyActionListener");

  void beforeCheck(AnActionEvent event);
}
