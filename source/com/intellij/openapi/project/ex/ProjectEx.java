package com.intellij.openapi.project.ex;

import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.project.Project;

public interface ProjectEx extends Project {
  boolean isSavePathsRelative();

  void setSavePathsRelative(boolean b);

  ReplacePathToMacroMap getMacroReplacements();

  ExpandMacroToPathMap getExpandMacroReplacements();

  boolean isDummy();
}
