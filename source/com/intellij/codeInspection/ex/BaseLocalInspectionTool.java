package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.LocalInspectionTool;

/**
 * User: anna
 * Date: Dec 27, 2004
 */
public abstract class BaseLocalInspectionTool extends LocalInspectionTool {
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  public boolean isEnabledByDefault() {
    return true;
  }
}
