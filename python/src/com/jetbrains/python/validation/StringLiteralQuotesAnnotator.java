/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl;

import java.util.List;

/**
 * Looks for well-formedness of string constants.
 *
 * @author dcheryasov
 */
public class StringLiteralQuotesAnnotator extends PyAnnotator {
  public static final String MISSING_Q = "Missing closing quote";
  private static final String TRIPLE_QUOTES = "\"\"\"";
  private static final String TRIPLE_APOS = "'''";

  public void visitPyStringLiteralExpression(final PyStringLiteralExpression node) {
    List<ASTNode> stringNodes = node.getStringNodes();
    for (ASTNode stringNode : stringNodes) {
      boolean foundError;
      String nodeText = stringNode.getText();
      int index = PyStringLiteralExpressionImpl.getPrefixLength(nodeText);
      String unprefixed = nodeText.substring(index);
      if (StringUtil.startsWith(unprefixed, TRIPLE_QUOTES)) {
        foundError = checkTripleQuotedString(stringNode, unprefixed, TRIPLE_QUOTES);
      }
      else if (StringUtil.startsWith(unprefixed, TRIPLE_APOS)) {
        foundError = checkTripleQuotedString(stringNode, unprefixed, TRIPLE_APOS);
      }
      else {
        foundError = checkQuotedString(stringNode, unprefixed);
      }
      if (foundError) break;
    }
  }

  private boolean checkQuotedString(ASTNode stringNode, String nodeText) {
    char firstQuote = nodeText.charAt(0);
    int lastChar = nodeText.length()-1;
    if (lastChar == 0 || nodeText.charAt(lastChar) != firstQuote ||
        (nodeText.charAt(lastChar-1) == '\\' && (lastChar == 1 || nodeText.charAt(lastChar-2) != '\\'))) {
      getHolder().createErrorAnnotation(stringNode, MISSING_Q + " [" + firstQuote + "]");
      return true;
    }
    return false;
  }

  private boolean checkTripleQuotedString(ASTNode stringNode, String text, final String quotes) {
    if (text.length() < 6  || !text.endsWith(quotes)) {
      int startOffset = StringUtil.trimTrailing(stringNode.getText()).lastIndexOf('\n');
      if (startOffset < 0) {
        startOffset = stringNode.getTextRange().getStartOffset();
      }
      else {
        startOffset = stringNode.getTextRange().getStartOffset() + startOffset + 1;
      }
      TextRange highlightRange = new TextRange(startOffset, stringNode.getTextRange().getEndOffset());
      getHolder().createErrorAnnotation(highlightRange, "Missing closing triple quotes");
      return true;
    }
    return false;
  }
}
