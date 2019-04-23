package com.intellij.bash.formatter;

import com.intellij.application.options.CodeStyle;
import com.intellij.bash.BashFileType;
import com.intellij.bash.BashLanguage;
import com.intellij.bash.BashTypes;
import com.intellij.bash.lexer.BashTokenTypes;
import com.intellij.formatting.IndentInfo;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.lineIndent.LineIndentProvider;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class BashLineIndentProvider implements LineIndentProvider {

  @Nullable
  @Override
  public String getLineIndent(@NotNull Project project, @NotNull Editor editor, @Nullable Language language, int offset) {
    if (offset > 0) {
      BashSemanticEditorPosition position = getPosition(editor, offset - 1);
      if (position.isAt(BashTokenTypes.LINEFEED) || position.isAt(BashTokenTypes.WHITESPACE)) {
        moveAtEndOfPreviousLine(position);
        if (position.isAtAnyOf(BashTypes.DO, BashTypes.LEFT_CURLY, BashTypes.ELSE, BashTypes.THEN)) {
          return getIndentString(editor, position.getStartOffset(), true);
        } else if (position.isAt(BashTypes.CASE_END)) {
          return getIndentString(editor, position.getStartOffset(), false);
        } else if (isInCasePattern(editor, position)) {
          return getIndentString(editor, position.getStartOffset(), true);
        }
      }
    }
    return null;
  }

  @Override
  public boolean isSuitableFor(@Nullable Language language) {
    return language instanceof BashLanguage;
  }

  private boolean isInCasePattern(@NotNull Editor editor, BashSemanticEditorPosition position) {
    CharSequence docChars = editor.getDocument().getCharsSequence();
    int lineStart = CharArrayUtil.shiftBackwardUntil(docChars, position.getStartOffset(), "\n") + 1;
    if (lineStart >= 0) {
      BashSemanticEditorPosition possiblePatternPosition = getPosition(editor, lineStart);
      possiblePatternPosition.moveAfterOptionalMix(BashTokenTypes.WHITESPACE);
      possiblePatternPosition.moveAfterOptionalMix(BashTokenTypes.WORD);
      possiblePatternPosition.moveAfterOptionalMix(BashTokenTypes.WHITESPACE);
      return possiblePatternPosition.isAt(BashTypes.RIGHT_PAREN);
    }
    return false;
  }

  private void moveAtEndOfPreviousLine(BashSemanticEditorPosition position) {
    position.moveBeforeOptionalMix(BashTokenTypes.WHITESPACE);
    if (position.isAt(BashTokenTypes.LINEFEED)) {
      position.moveBefore();
      position.moveBeforeOptionalMix(BashTokenTypes.WHITESPACE);
    }
  }

  @NotNull
  private String getIndentString(@NotNull Editor editor, int offset, boolean shouldExpand) {
    CodeStyleSettings settings = CodeStyle.getSettings(editor);
    CommonCodeStyleSettings.IndentOptions indentOptions = settings.getIndentOptions(BashFileType.INSTANCE);
    CharSequence docChars = editor.getDocument().getCharsSequence();

    String baseIndent = "";
    if (offset > 0) {
      int indentStart = CharArrayUtil.shiftBackwardUntil(docChars, offset, "\n") + 1;
      if (indentStart >= 0) {
        int indentEnd = CharArrayUtil.shiftForward(docChars, indentStart, " \t");
        int diff = indentEnd - indentStart;
        if (diff > 0) {
          if (shouldExpand) {
            baseIndent = docChars.subSequence(indentStart, indentEnd).toString();
          }
          else {
            if (diff >= indentOptions.INDENT_SIZE) {
              baseIndent = docChars.subSequence(indentStart, indentEnd - indentOptions.INDENT_SIZE).toString();
            }
          }
        }
      }
    }
    if (shouldExpand) {
      baseIndent += new IndentInfo(0, indentOptions.INDENT_SIZE, 0).generateNewWhiteSpace(indentOptions);
    }
    return baseIndent;
  }

  private BashSemanticEditorPosition getPosition(@NotNull Editor editor, int offset) {
    return BashSemanticEditorPosition.createEditorPosition((EditorEx) editor, offset);
  }
}
