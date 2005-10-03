package com.intellij.openapi.diff.impl.util;

import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NonNls;

public interface FocusDiffSide {
  @NonNls String FOCUSED_DIFF_SIDE = "focusedDiffSide";
  Editor getEditor();
  int[] getFragmentStartingLines();
}
