package org.jetbrains.plugins.textmate.editor;

import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.language.TextMateFileType;
import org.jetbrains.plugins.textmate.language.preferences.TextMateBracePair;

public class TextMateBackspaceHandler extends BackspaceHandlerDelegate {
  @Override
  public void beforeCharDeleted(char c, @NotNull PsiFile file, @NotNull Editor editor) {
  }

  @Override
  public boolean charDeleted(char c, PsiFile file, @NotNull Editor editor) {
    if (file.getFileType() == TextMateFileType.INSTANCE) {
      final int offset = editor.getCaretModel().getOffset();
      EditorHighlighter highlighter = ((EditorEx)editor).getHighlighter();
      HighlighterIterator iterator = highlighter.createIterator(offset);
      if (offset == 0 && iterator.atEnd()) {
        return false;
      }
      final IElementType tokenType = iterator.getTokenType();
      if (tokenType != null) {
        String scopeSelector = tokenType.toString();
        final TextMateBracePair pairForChar = TextMateEditorUtils.getSmartTypingPairForLeftChar(c, scopeSelector);
        if (pairForChar != null) {
          final Document document = editor.getDocument();
          if (document.getTextLength() > offset) {
            char prevChar = document.getCharsSequence().charAt(offset);
            if (prevChar == pairForChar.rightChar) {
              document.deleteString(offset, offset + 1);
              return true;
            }
          }
        }
      }
    }
    return false;
  }
}
