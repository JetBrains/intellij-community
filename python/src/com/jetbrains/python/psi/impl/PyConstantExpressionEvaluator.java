package com.jetbrains.python.psi.impl;

import com.jetbrains.python.psi.PyBoolLiteralExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;

/**
 * @author yole
 */
public class PyConstantExpressionEvaluator {
  private PyConstantExpressionEvaluator() {
  }

  @Nullable
  public static Object evaluate(final PyExpression expr) {
    if (expr instanceof PyNumericLiteralExpression) {
      final PyNumericLiteralExpression numericLiteral = (PyNumericLiteralExpression)expr;
      if (numericLiteral.isIntegerLiteral()) {
        final BigInteger value = numericLiteral.getBigIntegerValue();
        if ((long)value.intValue() == value.longValue()) {
          return value.intValue();
        }
      }
    }
    if (expr instanceof PyBoolLiteralExpression) {
      return ((PyBoolLiteralExpression)expr).getValue();
    }
    if (expr instanceof PyReferenceExpression) {
      final String text = expr.getText();
      if ("true".equals(text) || "True".equals(text)) {
        return true;
      }
      if ("false".equals(text) || "False".equals(text)) {
        return false;
      }
    }
    return null;
  }

  public static boolean evaluateBoolean(final PyExpression expr, boolean defaultValue) {
    Object result = evaluate(expr);
    if (result instanceof Boolean) {
      return (Boolean)result;
    }
    else {
      return defaultValue;
    }
  }

  public static boolean evaluateBoolean(final PyExpression expr) {
    return evaluateBoolean(expr, true);
  }
}
