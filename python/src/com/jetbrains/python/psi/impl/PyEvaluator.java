package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;

import java.util.HashSet;
import java.util.Set;

/**
 * @author yole
 */
public class PyEvaluator {
  private Set<PyExpression> myVisited = new HashSet<PyExpression>();

  public String evaluate(PyExpression expr) {
    if (expr == null || myVisited.contains(expr)) {
      return null;
    }
    myVisited.add(expr);
    if (expr instanceof PyCallExpression) {
      return evaluateCall((PyCallExpression)expr);
    }
    else if (expr instanceof PyReferenceExpression) {
      return evaluateReferenceExpression((PyReferenceExpression)expr);
    }
    else if (expr instanceof PyStringLiteralExpression) {
      return ((PyStringLiteralExpression)expr).getStringValue();
    }
    return null;
  }

  protected String evaluateReferenceExpression(PyReferenceExpression expr) {
    if (expr.getQualifier() == null) {
      PsiElement result = expr.getReference(PyResolveContext.noImplicits()).resolve();
      if (result instanceof PyTargetExpression) {
        result = ((PyTargetExpression)result).findAssignedValue();
      }
      if (result instanceof PyExpression) {
        return evaluate((PyExpression)result);
      }
    }
    return null;
  }

  protected String evaluateCall(PyCallExpression call) {
    final PyExpression[] args = call.getArguments();
    if (call.isCalleeText(PyNames.REPLACE) && args.length == 2) {
      final PyExpression callee = call.getCallee();
      if (!(callee instanceof PyQualifiedExpression)) return null;
      final PyExpression qualifier = ((PyQualifiedExpression)callee).getQualifier();
      String result = evaluate(qualifier);
      if (result == null) return null;
      String arg1 = evaluate(args[0]);
      String arg2 = evaluate(args[1]);
      if (arg1 == null || arg2 == null) return null;
      return result.replace(arg1, arg2);
    }
    return null;
  }
}
