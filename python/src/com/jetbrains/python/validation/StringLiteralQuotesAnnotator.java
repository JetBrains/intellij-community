/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Looks for well-formedness of string constants.
 *
 * @author dcheryasov
 */
public class StringLiteralQuotesAnnotator extends PyAnnotator {
  private static final String TRIPLE_QUOTES = "\"\"\"";
  private static final String TRIPLE_APOS = "'''";

  public void visitPyStringLiteralExpression(final PyStringLiteralExpression node) {
    final List<ASTNode> stringNodes = node.getStringNodes();
    for (ASTNode stringNode : stringNodes) {
      final String nodeText = stringNode.getText();
      final int index = PyStringLiteralExpressionImpl.getPrefixLength(nodeText);
      final String unprefixed = nodeText.substring(index);
      final boolean foundError;
      if (StringUtil.startsWith(unprefixed, TRIPLE_QUOTES)) {
        foundError = checkTripleQuotedString(stringNode, unprefixed, TRIPLE_QUOTES);
      }
      else if (StringUtil.startsWith(unprefixed, TRIPLE_APOS)) {
        foundError = checkTripleQuotedString(stringNode, unprefixed, TRIPLE_APOS);
      }
      else {
        foundError = checkQuotedString(stringNode, unprefixed);
      }
      if (foundError) {
        break;
      }
    }
  }

  private boolean checkQuotedString(@NotNull ASTNode stringNode, @NotNull String nodeText) {
    final char firstQuote = nodeText.charAt(0);
    final char lastChar = nodeText.charAt(nodeText.length() - 1);
    int precedingBackslashCount = 0;
    for (int i = nodeText.length() - 2; i >= 0; i--) {
      if (nodeText.charAt(i) == '\\') {
        precedingBackslashCount++;
      }
      else {
        break;
      }
    }
    if (nodeText.length() == 1 || lastChar != firstQuote || precedingBackslashCount % 2 != 0) {
      getHolder().createErrorAnnotation(stringNode, PyBundle.message("ANN.missing.closing.quote", firstQuote));
      return true;
    }
    return false;
  }

  private boolean checkTripleQuotedString(@NotNull ASTNode stringNode, @NotNull String text, @NotNull String quotes) {
    if (text.length() < 6 || !text.endsWith(quotes)) {
      int startOffset = StringUtil.trimTrailing(stringNode.getText()).lastIndexOf('\n');
      if (startOffset < 0) {
        startOffset = stringNode.getTextRange().getStartOffset();
      }
      else {
        startOffset = stringNode.getTextRange().getStartOffset() + startOffset + 1;
      }
      final TextRange highlightRange = new TextRange(startOffset, stringNode.getTextRange().getEndOffset());
      getHolder().createErrorAnnotation(highlightRange, PyBundle.message("ANN.missing.closing.triple.quotes"));
      return true;
    }
    return false;
  }
}
