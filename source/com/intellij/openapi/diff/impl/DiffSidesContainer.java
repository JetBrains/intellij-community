package com.intellij.openapi.diff.impl;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;

public interface DiffSidesContainer {
  void setCurrentSide(DiffSideView viewSide);
  Project getProject();
  void showSource(OpenFileDescriptor descriptor);
}
