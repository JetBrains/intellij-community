package com.jetbrains.python.ast.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.ast.*;
import com.jetbrains.python.ast.controlFlow.AstScopeOwner;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Assorted utility methods for Python code insight.
 *
 * These methods don't depend on the Python runtime.
 *
 * @see PyPsiUtilsCore for utilities used in Python PSI API
 */
@ApiStatus.Experimental
public final class PyUtilCore {

  private PyUtilCore() {
  }

  /**
   * @see PyUtil#flattenedParensAndTuples
   */
  private static List<PyAstExpression> unfoldParentheses(PyAstExpression[] targets, List<PyAstExpression> receiver,
                                                         boolean unfoldListLiterals, boolean unfoldStarExpressions) {
    // NOTE: this proliferation of instanceofs is not very beautiful. Maybe rewrite using a visitor.
    for (PyAstExpression exp : targets) {
      if (exp instanceof PyAstParenthesizedExpression parenExpr) {
        unfoldParentheses(new PyAstExpression[]{parenExpr.getContainedExpression()}, receiver, unfoldListLiterals, unfoldStarExpressions);
      }
      else if (exp instanceof PyAstTupleExpression tupleExpr) {
        unfoldParentheses(tupleExpr.getElements(), receiver, unfoldListLiterals, unfoldStarExpressions);
      }
      else if (exp instanceof PyAstListLiteralExpression listLiteral && unfoldListLiterals) {
        unfoldParentheses(listLiteral.getElements(), receiver, true, unfoldStarExpressions);
      }
      else if (exp instanceof PyAstStarExpression && unfoldStarExpressions) {
        unfoldParentheses(new PyAstExpression[]{((PyAstStarExpression)exp).getExpression()}, receiver, unfoldListLiterals, true);
      }
      else if (exp != null) {
        receiver.add(exp);
      }
    }
    return receiver;
  }

  /**
   * Flattens the representation of every element in targets, and puts all results together.
   * Elements of every tuple nested in target item are brought to the top level: (a, (b, (c, d))) -> (a, b, c, d)
   * Typical usage: {@code flattenedParensAndTuples(some_tuple.getExpressions())}.
   *
   * @param targets target elements.
   * @return the list of flattened expressions.
   */
  @NotNull
  public static List<PyAstExpression> flattenedParensAndTuples(PyAstExpression... targets) {
    return unfoldParentheses(targets, new ArrayList<>(targets.length), false, false);
  }

  @NotNull
  public static List<PyAstExpression> flattenedParensAndLists(PyAstExpression... targets) {
    return unfoldParentheses(targets, new ArrayList<>(targets.length), true, true);
  }

  @NotNull
  public static List<PyAstExpression> flattenedParensAndStars(PyAstExpression... targets) {
    return unfoldParentheses(targets, new ArrayList<>(targets.length), false, true);
  }

  @Nullable
  public static PyAstLoopStatement getCorrespondingLoop(@NotNull PsiElement breakOrContinue) {
    return breakOrContinue instanceof PyAstContinueStatement || breakOrContinue instanceof PyAstBreakStatement
           ? getCorrespondingLoopImpl(breakOrContinue)
           : null;
  }

  @Nullable
  private static PyAstLoopStatement getCorrespondingLoopImpl(@NotNull PsiElement element) {
    final PyAstLoopStatement loop = PsiTreeUtil.getParentOfType(element, PyAstLoopStatement.class, true, AstScopeOwner.class);

    if (loop instanceof PyAstStatementWithElse && PsiTreeUtil.isAncestor(((PyAstStatementWithElse)loop).getElsePart(), element, true)) {
      return getCorrespondingLoopImpl(loop);
    }

    return loop;
  }

  /**
   * @return true if passed {@code element} is a method (this means a function inside a class) named {@code __init__} or {@code __new__}.
   * @see PyUtil#isInitMethod(PsiElement)
   * @see PyUtil#isNewMethod(PsiElement)
   * @see PyUtil#turnConstructorIntoClass(PyFunction)
   */
  @Contract("null -> false")
  public static boolean isInitOrNewMethod(@Nullable PsiElement element) {
    final PyAstFunction function = ObjectUtils.tryCast(element, PyAstFunction.class);
    if (function == null) return false;

    final String name = function.getName();
    return (PyNames.INIT.equals(name) || PyNames.NEW.equals(name)) && function.getContainingClass() != null;
  }

  public static boolean isStringLiteral(@Nullable PyAstStatement stmt) {
    if (stmt instanceof PyAstExpressionStatement) {
      final PyAstExpression expr = ((PyAstExpressionStatement)stmt).getExpression();
      if (expr instanceof PyAstStringLiteralExpression) {
        return true;
      }
    }
    return false;
  }

  /**
   * Counts initial underscores of an identifier.
   *
   * @param name identifier
   * @return 0 if null or no initial underscores found, 1 if there's only one underscore, 2 if there's two or more initial underscores.
   */
  public static int getInitialUnderscores(@Nullable String name) {
    return name == null ? 0 : name.startsWith("__") ? 2 : name.startsWith(PyNames.UNDERSCORE) ? 1 : 0;
  }

  @Nullable
  public static List<String> strListValue(PyAstExpression value) {
    while (value instanceof PyAstParenthesizedExpression) {
      value = ((PyAstParenthesizedExpression)value).getContainedExpression();
    }
    if (value instanceof PyAstSequenceExpression) {
      final PyAstExpression[] elements = ((PyAstSequenceExpression)value).getElements();
      List<String> result = new ArrayList<>(elements.length);
      for (PyAstExpression element : elements) {
        if (!(element instanceof PyAstStringLiteralExpression)) {
          return null;
        }
        result.add(((PyAstStringLiteralExpression)element).getStringValue());
      }
      return result;
    }
    return null;
  }
}
