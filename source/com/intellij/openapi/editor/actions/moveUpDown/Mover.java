package com.intellij.openapi.editor.actions.moveUpDown;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;

interface Mover {
  LineRange getRangeToMove(Editor editor, PsiFile file, boolean isDown);
  int getOffsetToMoveTo(Editor editor, PsiFile file, LineRange range, boolean isDown);
}
