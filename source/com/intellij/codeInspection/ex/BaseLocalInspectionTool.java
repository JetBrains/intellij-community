package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.LocalInspectionTool;

/**
 * User: anna
 * Date: Dec 27, 2004
 */
public abstract class BaseLocalInspectionTool extends LocalInspectionTool {
  public static final String GROUP_LOCAL_CODE_ANALYSIS = "Local Code Analysis";

  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  public boolean isEnabledByDefault() {
    return true;
  }
}
