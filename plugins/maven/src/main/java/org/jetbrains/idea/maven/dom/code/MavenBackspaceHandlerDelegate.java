package org.jetbrains.idea.maven.dom.code;

import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;

public class MavenBackspaceHandlerDelegate extends BackspaceHandlerDelegate {
  @Override
  public void beforeCharDeleted(char c, PsiFile file, Editor editor) {
  }

  @Override
  public boolean charDeleted(char c, PsiFile file, Editor editor) {
    if (c != '{') return false;
    if (!MavenTypedHandlerDelegate.shouldProcess(file)) return false;

    int offset = editor.getCaretModel().getOffset();
    CharSequence text = editor.getDocument().getCharsSequence();
    if (offset <= text.length() && text.charAt(offset) == '}') {
      editor.getDocument().deleteString(offset, offset + 1);
      return true;
    }
    return false;
  }
}