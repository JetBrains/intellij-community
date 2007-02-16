package com.intellij.openapi.project.ex;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.impl.stores.IProjectStore;

public interface ProjectEx extends Project {
  boolean isSavePathsRelative();

  void setSavePathsRelative(boolean b);

  boolean isDummy();

  IProjectStore getStateStore();
}
