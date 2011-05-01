package com.jetbrains.python.validation;

import com.intellij.lang.ASTNode;
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
      getHolder().createErrorAnnotation(stringNode, "Missing closing triple quotes");
      return true;
    }
    return false;
  }
}
