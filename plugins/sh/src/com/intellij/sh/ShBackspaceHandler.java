package com.intellij.sh;

import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.sh.psi.ShFile;
import org.jetbrains.annotations.NotNull;

public class ShBackspaceHandler extends BackspaceHandlerDelegate {
  @Override
  public void beforeCharDeleted(char c, @NotNull PsiFile file, @NotNull Editor editor) {
  }

  @Override
  public boolean charDeleted(char c, @NotNull PsiFile file, @NotNull Editor editor) {
    if ((c == '`' || c == '\'') && file instanceof ShFile) {
      int offset = editor.getCaretModel().getOffset();
      if (offset < 0) return false;
      CharSequence charsSequence = editor.getDocument().getCharsSequence();
      if (offset >= charsSequence.length()) return false;
      if (charsSequence.charAt(offset) != c) return false;

      editor.getDocument().deleteString(offset, offset + 1);
      return true;
    }
    return false;
  }
}
