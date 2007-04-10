package com.intellij.openapi.project.ex;

import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface ProjectEx extends Project {
  boolean isSavePathsRelative();

  void setSavePathsRelative(boolean b);

  boolean isDummy();

  @NotNull
  IProjectStore getStateStore();

  void init();

  boolean isOptimiseTestLoadSpeed();

  void setOptimiseTestLoadSpeed(boolean optimiseTestLoadSpeed);
}
