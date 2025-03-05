package org.jetbrains.plugins.textmate.editor;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.TextMateFileType;
import org.jetbrains.plugins.textmate.TextMateService;
import org.jetbrains.plugins.textmate.language.preferences.TextMateAutoClosingPair;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;

import java.util.Set;

import static org.jetbrains.plugins.textmate.editor.TextMateEditorUtils.getSmartTypingPairs;

public class TextMateTypedHandler extends TypedHandlerDelegate {
  @Override
  public @NotNull Result beforeCharTyped(char c,
                                         @NotNull Project project,
                                         @NotNull Editor editor,
                                         @NotNull PsiFile file,
                                         @NotNull FileType fileType) {
    if (fileType == TextMateFileType.INSTANCE) {
      if (c == '\'' || c == '"' || c == '`') {
        if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE) {
          return Result.CONTINUE;
        }
      }
      else if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) {
        return Result.CONTINUE;
      }

      final int offset = editor.getCaretModel().getOffset();
      @Nullable TextMateScope scopeSelector = TextMateEditorUtils.getCurrentScopeSelector((EditorEx)editor);

      final Document document = editor.getDocument();
      final TextMateAutoClosingPair pairForRightChar = findSingleCharSmartTypingPair(c, scopeSelector);
      if (pairForRightChar != null) {
        if (offset < document.getTextLength() && document.getCharsSequence().charAt(offset) == c) {
          EditorModificationUtil.moveCaretRelatively(editor, 1);
          return Result.STOP;
        }
      }
    }
    return Result.CONTINUE;
  }

  @Override
  public @NotNull Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (file.getFileType() == TextMateFileType.INSTANCE) {
      if (c == '\'' || c == '"' || c == '`') {
        if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE) {
          return Result.CONTINUE;
        }
      }
      else if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) {
        return Result.CONTINUE;
      }

      final int offset = editor.getCaretModel().getOffset();
      @Nullable TextMateScope scopeSelector = TextMateEditorUtils.getCurrentScopeSelector((EditorEx)editor);
      CharSequence sequence = editor.getDocument().getCharsSequence();
      final TextMateAutoClosingPair autoInsertingPair = findAutoInsertingPair(offset, sequence, scopeSelector);
      if (autoInsertingPair != null) {
        int rightBraceEndOffset = offset + autoInsertingPair.getRight().length();
        // has a right brace already
        if (rightBraceEndOffset < sequence.length() &&
            StringUtil.equals(autoInsertingPair.getRight(), sequence.subSequence(offset, rightBraceEndOffset))) {
          return Result.CONTINUE;
        }
        if (StringUtil.equals(autoInsertingPair.getLeft(), autoInsertingPair.getRight())) {
          // letter on the right
          if (offset < sequence.length() && Character.isLetterOrDigit(sequence.charAt(offset))) {
            return Result.CONTINUE;
          }
          int leftBraceStartOffset = offset - autoInsertingPair.getLeft().length();
          // letter on the left
          if (leftBraceStartOffset > 0 && Character.isLetterOrDigit(sequence.charAt(leftBraceStartOffset - 1))) {
            return Result.CONTINUE;
          }
        }
        EditorModificationUtilEx.insertStringAtCaret(editor, autoInsertingPair.getRight().toString(), true, false);
        return Result.STOP;
      }
    }
    return Result.CONTINUE;
  }

  private static TextMateAutoClosingPair findSingleCharSmartTypingPair(char closingChar, @Nullable TextMateScope currentSelector) {
    if (!TextMateService.getInstance().getPreferenceRegistry().isPossibleRightSmartTypingBrace(closingChar)) {
      return null;
    }
    Set<TextMateAutoClosingPair> pairs = getSmartTypingPairs(currentSelector);
    for (TextMateAutoClosingPair pair : pairs) {
      if (pair.getRight().length() == 1 && pair.getRight().charAt(0) == closingChar) {
        return pair;
      }
    }
    return null;
  }

  public static TextMateAutoClosingPair findAutoInsertingPair(int offset,
                                                              @NotNull CharSequence fileText,
                                                              @Nullable TextMateScope currentScope) {
    if (offset == 0 || !TextMateService.getInstance().getPreferenceRegistry().isPossibleLeftSmartTypingBrace(fileText.charAt(offset - 1))) {
      return null;
    }
    Set<TextMateAutoClosingPair> pairs = getSmartTypingPairs(currentScope);
    for (TextMateAutoClosingPair pair : pairs) {
      int startOffset = offset - pair.getLeft().length();
      if (startOffset >= 0 && StringUtil.equals(pair.getLeft(), fileText.subSequence(startOffset, offset))) {
        return pair;
      }
    }
    return null;
  }
}
