/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.validation;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.text.CharArrayUtil;
import com.jetbrains.python.codeInsight.fstrings.FStringParser;
import com.jetbrains.python.codeInsight.fstrings.FStringParser.Fragment;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.python.psi.PyUtil.StringNodeInfo;

/**
 * @author Mikhail Golubev
 */
public class FStringsAnnotator extends PyAnnotator {
  @Override
  public void visitPyStringLiteralExpression(PyStringLiteralExpression pyString) {
    for (ASTNode node : pyString.getStringNodes()) {
      final StringNodeInfo nodeInfo = new StringNodeInfo(node);
      final String nodeText = node.getText();
      if (nodeInfo.isFormatted()) {
        final int nodeContentEnd = nodeInfo.getContentRange().getEndOffset();
        final FStringParser.ParseResult result = FStringParser.parse(nodeText);
        TextRange unclosedBraceRange = null;
        for (Fragment fragment : result.getFragments()) {
          final int fragLeftBrace = fragment.getLeftBraceOffset();
          final int fragContentEnd = fragment.getContentEndOffset();
          final int fragRightBrace = fragment.getRightBraceOffset();

          final TextRange wholeFragmentRange = TextRange.create(fragLeftBrace, fragRightBrace == -1 ? nodeContentEnd : fragRightBrace + 1);
          if (fragment.getDepth() > 2) {
            // Do not report anything about expression fragments nested deeper that three times
            if (fragment.getDepth() == 3) {
              report("Expression fragment inside f-string is nested too deeply", wholeFragmentRange, node);
            }
            continue;
          }
          if (CharArrayUtil.isEmptyOrSpaces(nodeText, fragLeftBrace + 1, fragContentEnd) && fragContentEnd < nodeContentEnd) {
            final TextRange range = TextRange.create(fragLeftBrace, fragContentEnd + 1);
            report("Empty expression fragments are not allowed inside f-strings", range, node);
          }
          if (fragRightBrace == -1 && unclosedBraceRange == null) {
            unclosedBraceRange = wholeFragmentRange;
          }
          if (fragment.getFirstHashOffset() != -1) {
            final TextRange range = TextRange.create(fragment.getFirstHashOffset(), fragment.getContentEndOffset());
            report("Expression fragments inside f-strings cannot include line comments", range, node);
          }
          for (int i = fragLeftBrace + 1; i < fragment.getContentEndOffset(); i++) {
            if (nodeText.charAt(i) == '\\') {
              reportCharacter("Expression fragments inside f-strings cannot include backslashes", i, node);
            }
          }
          // Do not warn about illegal conversion character if '!' is right before closing quotes 
          if (fragContentEnd < nodeContentEnd && nodeText.charAt(fragContentEnd) == '!' && fragContentEnd + 1 < nodeContentEnd) {
            final char conversionChar = nodeText.charAt(fragContentEnd + 1);
            // No conversion character -- highlight only "!"
            if (fragContentEnd + 1 == fragRightBrace || conversionChar == ':') {
              reportCharacter("Conversion character is expected: should be one of 's', 'r', 'a'", fragContentEnd, node);
            }
            // Wrong conversion character -- highlight both "!" and the following symbol
            else if ("sra".indexOf(conversionChar) < 0) {
              final TextRange range = TextRange.from(fragContentEnd, 2);
              report("Illegal conversion character '" + conversionChar + "': should be one of 's', 'r', 'a'", range, node);
            }
          }
        }
        for (Integer offset : result.getSingleRightBraces()) {
          reportCharacter("Single '}' is not allowed inside f-strings", offset, node);
        }
        if (unclosedBraceRange != null) {
          report("'}' is expected", unclosedBraceRange, node);
        }
      }
    }
  }

  private void report(@NotNull String message, @NotNull TextRange range, @NotNull ASTNode node) {
    getHolder().createErrorAnnotation(range.shiftRight(node.getTextRange().getStartOffset()), message);
  }

  private void reportCharacter(@NotNull String message, int offset, @NotNull ASTNode node) {
    report(message, TextRange.from(offset, 1), node);
  }
}
