package com.intellij.openapi.editor.actions.moveUpDown;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;

interface Mover {
  InsertionInfo getInsertionInfo(Editor editor, PsiFile file, boolean isDown);
}
