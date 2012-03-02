package com.jetbrains.python.refactoring;

import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.python.PyTokenTypes.*;

/**
 * @author Dennis.Ushakov
 */
public class PyReplaceExpressionUtil implements PyElementTypes {

  private PyReplaceExpressionUtil() {}

  public static boolean isNeedParenthesis(@NotNull final PyElement oldExpr, @NotNull final PyElement newExpr) {
    final PyElement parentExpr = (PyElement)oldExpr.getParent();
    if (parentExpr instanceof PyArgumentList) {
      return newExpr instanceof PyTupleExpression;
    }
    if (!(parentExpr instanceof PyExpression)) {
      return false;
    }
    int newPriority = getExpressionPriority(newExpr);
    int parentPriority = getExpressionPriority(parentExpr);
    if (parentPriority > newPriority) {
      return true;
    } else if (parentPriority == newPriority && parentPriority != 0) {
      if (parentExpr instanceof PyBinaryExpression) {
        PyBinaryExpression binaryExpression = (PyBinaryExpression)parentExpr;
        if (isNotAssociative(binaryExpression) && oldExpr.equals(binaryExpression.getRightExpression())) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isNotAssociative(@NotNull final PyBinaryExpression binaryExpression) {
    final IElementType opType = getOperationType(binaryExpression);
    return COMPARISON_OPERATIONS.contains(opType) || binaryExpression instanceof PySliceExpression ||
           opType == DIV || opType == PERC || opType == EXP;
  }

  private static int getExpressionPriority(PyElement expr) {
    int priority = 0;
    if (expr instanceof PySubscriptionExpression || expr instanceof PySliceExpression ||
        expr instanceof PyCallExpression) priority = 1;
    if (expr instanceof PyPrefixExpression) {
      final IElementType opType = getOperationType(expr);
      if (opType == PLUS || opType == MINUS || opType == TILDE) priority = 2;
      if (opType == NOT_KEYWORD) priority = 10;
    }
    if (expr instanceof PyBinaryExpression) {
      final IElementType opType = getOperationType(expr);
      if (opType == EXP) priority =  3;
      if (opType == MULT || opType == DIV || opType == PERC) priority =  4;
      if (opType == PLUS || opType == MINUS) priority =  5;
      if (opType == LTLT || opType == GTGT) priority = 6;
      if (opType == AND) priority = 7;
      if (opType == OR) priority = 8;
      if (COMPARISON_OPERATIONS.contains(opType)) priority = 9;
      if (opType == AND_KEYWORD) priority = 11;
    }
    if (expr instanceof PyLambdaExpression) priority = 12; 

    return -priority;
  }

  @NotNull
  private static IElementType getOperationType(@NotNull final PyElement expr) {
    if (expr instanceof PyBinaryExpression) return ((PyBinaryExpression)expr).getOperator();
    return ((PyPrefixExpression)expr).getOperator();
  }
}
