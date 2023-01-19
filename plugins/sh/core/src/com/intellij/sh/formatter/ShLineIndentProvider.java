// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.formatter;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.IndentInfo;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.lineIndent.LineIndentProvider;
import com.intellij.sh.ShFileType;
import com.intellij.sh.ShLanguage;
import com.intellij.sh.ShTypes;
import com.intellij.sh.lexer.ShTokenTypes;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShLineIndentProvider implements LineIndentProvider {
  @Nullable
  @Override
  public String getLineIndent(@NotNull Project project, @NotNull Editor editor, @Nullable Language language, int offset) {
    if (offset > 0) {
      ShSemanticEditorPosition position = getPosition(editor, offset - 1);
      if (position.isAt(ShTypes.LINEFEED) || position.isAt(ShTokenTypes.WHITESPACE)) {
        moveAtEndOfPreviousLine(position);
        if (position.isAtAnyOf(ShTypes.DO, ShTypes.LEFT_CURLY, ShTypes.ELSE, ShTypes.THEN)) {
          return getIndentString(editor, position.getStartOffset(), true);
        }
        else if (position.isAt(ShTypes.CASE_END)) {
          return getIndentString(editor, position.getStartOffset(), false);
        }
        else if (isInCasePattern(editor, position)) {
          return getIndentString(editor, position.getStartOffset(), true);
        }
      }
    }
    return null;
  }

  @Override
  public boolean isSuitableFor(@Nullable Language language) {
    return language instanceof ShLanguage;
  }

  private static boolean isInCasePattern(@NotNull Editor editor, ShSemanticEditorPosition position) {
    CharSequence docChars = editor.getDocument().getCharsSequence();
    int lineStart = CharArrayUtil.shiftBackwardUntil(docChars, position.getStartOffset(), "\n") + 1;
    if (lineStart >= 0) {
      ShSemanticEditorPosition possiblePatternPosition = getPosition(editor, lineStart);
      possiblePatternPosition.moveAfterOptionalMix(ShTokenTypes.WHITESPACE);
      possiblePatternPosition.moveAfterOptionalMix(ShTypes.WORD);
      possiblePatternPosition.moveAfterOptionalMix(ShTokenTypes.WHITESPACE);
      return possiblePatternPosition.isAt(ShTypes.RIGHT_PAREN);
    }
    return false;
  }

  private static void moveAtEndOfPreviousLine(ShSemanticEditorPosition position) {
    position.moveBeforeOptionalMix(ShTokenTypes.WHITESPACE);
    if (position.isAt(ShTypes.LINEFEED)) {
      position.moveBefore();
      position.moveBeforeOptionalMix(ShTokenTypes.WHITESPACE);
    }
  }

  @NotNull
  private static String getIndentString(@NotNull Editor editor, int offset, boolean shouldExpand) {
    CodeStyleSettings settings = CodeStyle.getSettings(editor);
    CommonCodeStyleSettings.IndentOptions indentOptions = settings.getIndentOptions(ShFileType.INSTANCE);
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

  private static ShSemanticEditorPosition getPosition(@NotNull Editor editor, int offset) {
    return ShSemanticEditorPosition.createEditorPosition(editor, offset);
  }
}
