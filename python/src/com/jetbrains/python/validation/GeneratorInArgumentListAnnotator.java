package com.jetbrains.python.validation;

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
          markError(expression, "Generator expression must be parenthesized if not sole argument");
        }
      }
    }
  }
}
