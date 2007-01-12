package com.intellij.openapi.project.ex;

import com.intellij.openapi.project.Project;

public interface ProjectEx extends Project {
  boolean isSavePathsRelative();

  void setSavePathsRelative(boolean b);

  boolean isDummy();
}
