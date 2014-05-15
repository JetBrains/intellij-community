package com.jetbrains.python.psi.impl;

import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Engine to evaluate expressions.
 * <p/>
 * Supports references and concatinations (sometimes!)
 *
 * @author Ilya.Kazakevich
 * @see {@link com.jetbrains.python.psi.impl.PyBlockEvaluator} (TODO: merge both classes?)
 */
public final class PyExpressionEvaluator {
  private static final int MAX_STEP = 100;

  private PyExpressionEvaluator() {
  }


  /**
   * Evaluate expression to string
   *
   * @param expression expression to evaluate
   * @return string (or empty if cant evaluate)
   */
  @NotNull
  public static String evaluateString(@NotNull final PyExpression expression) {
    final List<PyStringLiteralExpression> expressions = evaluateRaw(expression, PyStringLiteralExpression.class);
    final StringBuilder builder = new StringBuilder();
    for (final PyStringLiteralExpression literalExpression : expressions) {
      builder.append(literalExpression.getStringValue());
    }

    return builder.toString();
  }

  /**
   * Evaluate expression to long
   *
   * @param expression expression to evaluate
   * @return long (or 0 if cant evaluate)
   */
  public static long evaluateLong(@NotNull final PyExpression expression) {
    final List<PyNumericLiteralExpression> expressions = evaluateRaw(expression, PyNumericLiteralExpression.class);
    long result = 0L;
    for (final PyNumericLiteralExpression numericLiteralExpression : expressions) {
      final Long value = numericLiteralExpression.getLongValue();
      result += value != null ? value : 0L;
    }
    return result;
  }


  /**
   * Evaluate expression to pack of elements (some iterables could be evaluated to it)
   *
   * @param expression expression to evaluate
   * @param aClass     expected class of elements
   * @return pack of elements (or empty if cant evaluate)
   */
  @NotNull
  public static <T extends PyElement> List<T> evaluateIterable(@NotNull final PyExpression expression, @NotNull final Class<T> aClass) {
    final List<T> list = new ArrayList<T>();
    for (final PyElement possibleIterable : evaluateRaw(expression, PyElement.class)) {
      if ((possibleIterable instanceof PyParenthesizedExpression) || (possibleIterable instanceof PyListLiteralExpression)) {
        list.addAll(PsiTreeUtil.findChildrenOfType(possibleIterable, aClass));
      }
    }
    return list;
  }

  /**
   * Evaluates expression to some element
   * @param expression expression to evaluate
   * @param aClass expected element type
   * @param <T> expected element type
   * @return element or null if can't evaluate
   */
  @Nullable
  public static <T extends PyElement> T evaluateOne(@NotNull final PyExpression expression, @NotNull final Class<T> aClass) {
    final List<T> list = evaluateRaw(expression, aClass);
    if (list.isEmpty()) {
      return null;
    }
    return list.get(0);
  }


  /**
   * Evaluates expression to one or more elements (all elements should be concatinated).
   * I.e: i = 1 + 2. Evaluating "i" would lead to "1" and "2" separately (and not summed like in {@link #evaluateLong(com.jetbrains.python.psi.PyExpression)}
   * @param expression expression to evaluate
   * @param aClass expected elements type
   * @param <T> expected elements type
   * @return a pack of elements to concatinate or empty if can't evaluate
   */
  @NotNull
  public static <T extends PyElement> List<T> evaluateRaw(@NotNull final PyExpression expression, @NotNull final Class<T> aClass) {
    int step = 0;
    PyElement currentElement = expression;
    final List<T> result = new ArrayList<T>();
    while (step < MAX_STEP) {
      PyElement newElement = null;

      // TODO: Use visitor?
      if (currentElement instanceof PyReferenceExpression) {
        newElement = PyUtil.as(((PyReferenceExpression)currentElement).getReference().resolve(), PyElement.class);
      }
      else if (currentElement instanceof PyTargetExpression) {
        newElement = ((PyTargetExpression)currentElement).findAssignedValue();
      }
      else if (currentElement instanceof PyBinaryExpression) {
        final PyBinaryExpression newExpression = (PyBinaryExpression)currentElement;
        final PyElementType operator = newExpression.getOperator();
        if (PyTokenTypes.PLUS.equals(operator)) {
          final PyExpression leftExpression = newExpression.getLeftExpression();
          final PyExpression rightExpression = newExpression.getRightExpression();
          if (leftExpression != null) {
            result.addAll(evaluateRaw(leftExpression, aClass));
          }
          if (rightExpression != null) {
            result.addAll(evaluateRaw(rightExpression, aClass));
          }
        }
      }

      if (newElement == null) {
        break;
      }

      currentElement = newElement;
      step++;
    }
    final T elementToAdd = PyUtil.as(currentElement, aClass);
    if (elementToAdd != null) {
      result.add(elementToAdd);
    }
    return result;
  }
}
