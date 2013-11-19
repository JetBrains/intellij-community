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
package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;

import java.util.*;

/**
 * @author yole
 */
public class PyEvaluator {
  private Set<PyExpression> myVisited = new HashSet<PyExpression>();
  private Map<String, Object> myNamespace;
  private boolean myEvaluateCollectionItems = true;

  public void setNamespace(Map<String, Object> namespace) {
    myNamespace = namespace;
  }

  public void setEvaluateCollectionItems(boolean evaluateCollectionItems) {
    myEvaluateCollectionItems = evaluateCollectionItems;
  }

  public Object evaluate(PyExpression expr) {
    if (expr == null || myVisited.contains(expr)) {
      return null;
    }
    myVisited.add(expr);
    if (expr instanceof PyParenthesizedExpression) {
      return evaluate(((PyParenthesizedExpression) expr).getContainedExpression());
    }
    if (expr instanceof PySequenceExpression) {
      PyExpression[] elements = ((PySequenceExpression)expr).getElements();
      if (expr instanceof PyDictLiteralExpression) {
        Map<Object, Object> result = new HashMap<Object, Object>();
        for (PyKeyValueExpression keyValueExpression : ((PyDictLiteralExpression)expr).getElements()) {
          Object dictKey = evaluate(keyValueExpression.getKey());
          if (dictKey != null) {
            PyExpression value = keyValueExpression.getValue();
            result.put(dictKey, myEvaluateCollectionItems ? evaluate(value) : value);
          }
        }
        return result;
      }
      else {
        List<Object> result = new ArrayList<Object>();
        for (PyExpression element : elements) {
          result.add(myEvaluateCollectionItems ? evaluate(element) : element);
        }
        return result;
      }
    }
    if (expr instanceof PyCallExpression) {
      return evaluateCall((PyCallExpression)expr);
    }
    else if (expr instanceof PyReferenceExpression) {
      return evaluateReferenceExpression((PyReferenceExpression)expr);
    }
    else if (expr instanceof PyStringLiteralExpression) {
      return ((PyStringLiteralExpression)expr).getStringValue();
    }
    else if (expr instanceof PyBinaryExpression) {
      PyBinaryExpression binaryExpr = (PyBinaryExpression)expr;
      PyElementType op = binaryExpr.getOperator();
      if (op == PyTokenTypes.PLUS) {
        Object lhs = evaluate(binaryExpr.getLeftExpression());
        Object rhs = evaluate(binaryExpr.getRightExpression());
        if (lhs != null && rhs != null) {
          return concatenate(lhs, rhs);
        }
      }
    }
    return null;
  }

  public static Object concatenate(Object lhs, Object rhs) {
    if (lhs instanceof String && rhs instanceof String) {
      return (String) lhs + (String) rhs;
    }
    if (lhs instanceof List && rhs instanceof List) {
      List<Object> result = new ArrayList<Object>();
      result.addAll((List) lhs);
      result.addAll((List) rhs);
      return result;
    }
    return null;
  }

  protected Object evaluateReferenceExpression(PyReferenceExpression expr) {
    if (expr.getQualifier() == null) {
      if (myNamespace != null) {
        return myNamespace.get(expr.getReferencedName());
      }
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

  protected Object evaluateCall(PyCallExpression call) {
    final PyExpression[] args = call.getArguments();
    if (call.isCalleeText(PyNames.REPLACE) && args.length == 2) {
      final PyExpression callee = call.getCallee();
      if (!(callee instanceof PyQualifiedExpression)) return null;
      final PyExpression qualifier = ((PyQualifiedExpression)callee).getQualifier();
      Object result = evaluate(qualifier);
      if (result instanceof String) {
        Object arg1 = evaluate(args[0]);
        Object arg2 = evaluate(args[1]);
        if (arg1 instanceof String && arg2 instanceof String) {
          return ((String) result).replace((String) arg1, (String) arg2);
        }
      }
    }
    return null;
  }
}
