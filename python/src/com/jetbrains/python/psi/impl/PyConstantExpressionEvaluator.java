package com.jetbrains.python.psi.impl;

import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;

/**
 * @author yole
 */
public class PyConstantExpressionEvaluator {
  private PyConstantExpressionEvaluator() {
  }

  @Nullable
  public static Object evaluate(PyExpression expr) {
    if (expr instanceof PyNumericLiteralExpression) {
      final PyNumericLiteralExpression numericLiteral = (PyNumericLiteralExpression)expr;
      if (numericLiteral.isIntegerLiteral()) {
        final BigInteger value = numericLiteral.getBigIntegerValue();
        if ((long) value.intValue() == value.longValue()) {
          return value.intValue();
        }
      }
    }
    return null;
  }

}
