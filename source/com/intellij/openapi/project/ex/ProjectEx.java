package com.intellij.openapi.project.ex;

import com.intellij.openapi.project.Project;
import com.intellij.application.options.ExpandMacroToPathMap;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.application.options.ExpandMacroToPathMap;
import com.intellij.application.options.ReplacePathToMacroMap;

import java.util.Map;

public interface ProjectEx extends Project {
  void dispose();

  boolean isSavePathsRelative();

  void setSavePathsRelative(boolean b);

  ReplacePathToMacroMap getMacroReplacements();

  ExpandMacroToPathMap getExpandMacroReplacements();

  boolean isDummy();
}
