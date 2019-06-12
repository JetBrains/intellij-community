package org.jetbrains.plugins.textmate.editor;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.language.TextMateFileType;
import org.jetbrains.plugins.textmate.language.preferences.TextMateBracePair;

public class TextMateTypedHandler extends TypedHandlerDelegate {
  @NotNull
  @Override
  public Result beforeCharTyped(char c,
                                @NotNull Project project,
                                @NotNull Editor editor,
                                @NotNull PsiFile file,
                                @NotNull FileType fileType) {
    if (fileType == TextMateFileType.INSTANCE) {
      final int offset = editor.getCaretModel().getOffset();
      String scopeSelector = TextMateEditorUtils.getCurrentScopeSelector((EditorEx)editor);

      final Document document = editor.getDocument();
      final TextMateBracePair pairForRightChar = TextMateEditorUtils.getSmartTypingPairForRightChar(c, scopeSelector);
      if (pairForRightChar != null && c == pairForRightChar.rightChar) {
        if (offset < document.getTextLength() && document.getCharsSequence().charAt(offset) == c) {
          EditorModificationUtil.moveCaretRelatively(editor, 1);
          return Result.STOP;
        }
      }

      final TextMateBracePair pairForLeftChar = TextMateEditorUtils.getSmartTypingPairForLeftChar(c, scopeSelector);
      if (pairForLeftChar != null) {
        CharSequence chars = document.getCharsSequence();
        if (pairForLeftChar.leftChar == pairForLeftChar.rightChar) {
          final char prevChar = offset > 0 ? document.getCharsSequence().charAt(offset - 1) : ' ';
          final char nextChar = offset < document.getTextLength() ? document.getCharsSequence().charAt(offset) : ' ';
          if (!Character.isLetterOrDigit(prevChar) && prevChar != pairForLeftChar.leftChar &&
              !Character.isLetterOrDigit(nextChar) && nextChar != pairForLeftChar.rightChar) {
            EditorModificationUtil.insertStringAtCaret(editor, StringUtil.repeatSymbol(pairForLeftChar.leftChar, 2), true, 1);
            return Result.STOP;
          }
        }
        else if (alreadyHasEnding(chars, c, pairForLeftChar.rightChar, offset)) {
          return Result.CONTINUE;
        }
        else {
          EditorModificationUtil.insertStringAtCaret(editor,
                                                     String.valueOf(new char[]{pairForLeftChar.leftChar, pairForLeftChar.rightChar}),
                                                     true, 1);
          return Result.STOP;
        }
      }
    }
    return Result.CONTINUE;
  }

  private static boolean alreadyHasEnding(@NotNull final CharSequence chars, final char startChar, final char endChar, final int offset) {
    int i = offset;
    while (i < chars.length() && (chars.charAt(i) != startChar && chars.charAt(i) != endChar && chars.charAt(i) != '\n')) {
      i++;
    }
    return i < chars.length() && chars.charAt(i) == endChar;
  }
}
