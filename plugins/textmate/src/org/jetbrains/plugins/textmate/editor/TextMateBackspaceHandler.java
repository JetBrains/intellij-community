package org.jetbrains.plugins.textmate.editor;

import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.TextMateFileType;
import org.jetbrains.plugins.textmate.TextMateService;
import org.jetbrains.plugins.textmate.language.preferences.TextMateAutoClosingPair;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateElementType;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;

import java.util.Set;

import static org.jetbrains.plugins.textmate.editor.TextMateEditorUtils.getSmartTypingPairs;

public class TextMateBackspaceHandler extends BackspaceHandlerDelegate {
  @Override
  public void beforeCharDeleted(char c, @NotNull PsiFile file, @NotNull Editor editor) {
  }

  @Override
  public boolean charDeleted(char c, PsiFile file, @NotNull Editor editor) {
    if (file.getFileType() == TextMateFileType.INSTANCE) {
      final int offset = editor.getCaretModel().getOffset();
      EditorHighlighter highlighter = editor.getHighlighter();
      HighlighterIterator iterator = highlighter.createIterator(offset);
      if (offset == 0 && iterator.atEnd()) {
        return false;
      }
      final IElementType tokenType = iterator.getTokenType();
      if (tokenType instanceof TextMateElementType) {
        TextMateScope scopeSelector = ((TextMateElementType)tokenType).getScope();
        final TextMateAutoClosingPair pairForChar = findSingleCharSmartTypingPair(c, scopeSelector);
        if (pairForChar != null) {
          final Document document = editor.getDocument();
          int endOffset = offset + pairForChar.getRight().length();
          if (endOffset < document.getTextLength()) {
            if (StringUtil.equals(pairForChar.getRight(), document.getCharsSequence().subSequence(offset, endOffset))) {
              document.deleteString(offset, endOffset);
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private static TextMateAutoClosingPair findSingleCharSmartTypingPair(char openingChar, @Nullable TextMateScope currentSelector) {
    if (!TextMateService.getInstance().getPreferenceRegistry().isPossibleLeftSmartTypingBrace(openingChar)) {
      return null;
    }
    Set<TextMateAutoClosingPair> pairs = getSmartTypingPairs(currentSelector);
    for (TextMateAutoClosingPair pair : pairs) {
      if (pair.getLeft().length() == 1 && pair.getLeft().charAt(0) == openingChar) {
        return pair;
      }
    }
    return null;
  }
}
