package com.intellij.openapi.editor.actions.moveUpDown;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

interface Mover {
  /**
   * @return null if this mover is unable to find a place to move stuff at
   */
  @Nullable InsertionInfo getInsertionInfo(Editor editor, PsiFile file, boolean isDown);
}
