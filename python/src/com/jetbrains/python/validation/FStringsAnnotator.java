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
import com.jetbrains.python.codeInsight.fstrings.FStringParser.FragmentOffsets;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
        final int nodeOffset = node.getTextRange().getStartOffset();
        final int nodeContentEnd = nodeInfo.getContentRange().getEndOffset();
        final List<FragmentOffsets> fragments = FStringParser.parse(nodeText);
        boolean hasUnclosedBrace = false;
        for (FragmentOffsets fragment : fragments) {
          final int fragContentEnd = fragment.getContentEndOffset();
          if (CharArrayUtil.isEmptyOrSpaces(nodeText, fragment.getLeftBraceOffset() + 1, fragment.getContentEndOffset())) {
            report(fragment.getContentRange().shiftRight(nodeOffset), "Empty expressions are not allowed inside f-strings");
          }
          if (fragment.getRightBraceOffset() == -1) {
            hasUnclosedBrace = true;
          }
          for (int i = fragment.getLeftBraceOffset() + 1; i < fragment.getContentEndOffset(); i++) {
            final char c = nodeText.charAt(i);
            if (c == '\\') {
              reportCharacter(nodeOffset + i, "Expression fragments inside f-strings cannot include backslashes");
            }
            else if (c == '#') {
              reportCharacter(nodeOffset + i, "Expressions fragments inside f-strings cannot include '#'");
            }
          }
          // Do not warn about illegal conversion character if '!' is right before closing quotes 
          if (fragContentEnd < nodeContentEnd && nodeText.charAt(fragContentEnd) == '!' && fragContentEnd + 1 < nodeContentEnd) {
            final char conversionChar = nodeText.charAt(fragContentEnd + 1);
            final int offset = fragContentEnd + nodeOffset + 1;
            if (fragContentEnd + 1 == fragment.getRightBraceOffset() || conversionChar == ':') {
              reportEmpty(offset, "Conversion character is expected: should be one of 's', 'r', 'a'");
            }
            else if ("sra".indexOf(conversionChar) < 0) {
              reportCharacter(offset, "Illegal conversion character '" + conversionChar + "': should be one of 's', 'r', 'a'");
            }
          }
        }
        if (hasUnclosedBrace) {
          reportEmpty(nodeContentEnd + nodeOffset, "'}' is expected");
        }
      }
    }
  }

  private void report(@NotNull TextRange range, @NotNull String message) {
    getHolder().createErrorAnnotation(range, message);
  }

  private void reportEmpty(int offset, @NotNull String message) {
    getHolder().createErrorAnnotation(TextRange.from(offset, 0), message);
  }

  private void reportCharacter(int offset, @NotNull String message) {
    getHolder().createErrorAnnotation(TextRange.from(offset, 1), message);
  }
}
