package com.jetbrains.python.validation;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyGeneratorExpression;

/**
 * @author yole
 */
public class GeneratorInArgumentListAnnotator extends PyAnnotator {
  @Override
  public void visitPyArgumentList(PyArgumentList node) {
    if (node.getArguments().length > 1) {
      for (PyExpression expression : node.getArguments()) {
        if (expression instanceof PyGeneratorExpression) {
          ASTNode firstChildNode = expression.getNode().getFirstChildNode();
          if (firstChildNode.getElementType() != PyTokenTypes.LPAR) {
            markError(expression, "Generator expression must be parenthesized if not sole argument");
          }
        }
      }
    }
  }
}
