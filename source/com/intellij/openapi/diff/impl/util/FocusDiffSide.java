package com.intellij.openapi.diff.impl.util;

import com.intellij.openapi.editor.Editor;

public interface FocusDiffSide {
  String FOCUSED_DIFF_SIDE = "focusedDiffSide";
  Editor getEditor();
  int[] getFragmentStartingLines();
}
