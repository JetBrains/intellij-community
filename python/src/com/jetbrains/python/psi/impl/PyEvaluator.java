/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFQDNNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * TODO: Merge PythonDataflowUtil, {@link com.jetbrains.python.psi.impl.PyConstantExpressionEvaluator}  and {@link com.jetbrains.python.psi.impl.PyEvaluator} and all its inheritors and improve Abstract Interpretation
 *
 * @author yole
 */
public class PyEvaluator {
  private Set<PyExpression> myVisited = new HashSet<>();
  private Map<String, Object> myNamespace;
  private boolean myEvaluateCollectionItems = true;
  private boolean myEvaluateKeys = true;

  public void setNamespace(Map<String, Object> namespace) {
    myNamespace = namespace;
  }

  public void setEvaluateCollectionItems(boolean evaluateCollectionItems) {
    myEvaluateCollectionItems = evaluateCollectionItems;
  }

  /**
   * @param evaluateKeys evaluate keys for dicts or not (i.e. you wanna see string or StringLiteralExpressions as keys)
   */
  public void setEvaluateKeys(final boolean evaluateKeys) {
    myEvaluateKeys = evaluateKeys;
  }

  @Nullable
  public Object evaluate(@Nullable PyExpression expr) {
    if (expr == null || myVisited.contains(expr)) {
      return null;
    }
    PyUtil.verboseOnly(() ->PyPsiUtils.assertValid(expr));
    myVisited.add(expr);
    if (expr instanceof PyParenthesizedExpression) {
      return evaluate(((PyParenthesizedExpression)expr).getContainedExpression());
    }
    if (expr instanceof PySequenceExpression) {
      return evaluateSequenceExpression((PySequenceExpression)expr);
    }
    final Boolean booleanExpression = getBooleanExpression(expr);
    if (booleanExpression != null) { // support bool
      return booleanExpression;
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

  /**
   * TODO: Move to PyExpression? PyUtil?
   * True/False is bool literal in Py3K, but reference in Python2.
   *
   * @param expression expression to check
   * @return true if expression is boolean
   */
  @Nullable
  private static Boolean getBooleanExpression(@NotNull final PyExpression expression) {
    final boolean py3K = LanguageLevel.forElement(expression).isPy3K();
    if ((py3K && (expression instanceof PyBoolLiteralExpression))) {
      return ((PyBoolLiteralExpression)expression).getValue(); // Cool in Py2K
    }
    if ((!py3K && (expression instanceof PyReferenceExpression))) {
      final String text = ((PyReferenceExpression)expression).getReferencedName(); // Ref in Python2
      if (PyNames.TRUE.equals(text)) {
        return true;
      }
      if (PyNames.FALSE.equals(text)) {
        return false;
      }
    }

    return null;
  }

  /**
   * Evaluates some sequence (tuple, list)
   *
   * @param expr seq expression
   * @return evaluated seq
   */
  protected Object evaluateSequenceExpression(PySequenceExpression expr) {
    PyExpression[] elements = expr.getElements();
    if (expr instanceof PyDictLiteralExpression) {
      Map<Object, Object> result = new HashMap<>();
      for (final PyKeyValueExpression keyValueExpression : ((PyDictLiteralExpression)expr).getElements()) {
        addRecordFromDict(result, keyValueExpression.getKey(), keyValueExpression.getValue());
      }
      return result;
    }
    else {
      List<Object> result = new ArrayList<>();
      for (PyExpression element : elements) {
        result.add(myEvaluateCollectionItems ? evaluate(element) : element);
      }
      return result;
    }
  }

  public Object concatenate(Object lhs, Object rhs) {
    if (lhs instanceof String && rhs instanceof String) {
      return (String)lhs + (String)rhs;
    }
    if (lhs instanceof List && rhs instanceof List) {
      List<Object> result = new ArrayList<>();
      result.addAll((List)lhs);
      result.addAll((List)rhs);
      return result;
    }
    return null;
  }

  protected Object evaluateReferenceExpression(PyReferenceExpression expr) {
    if (!expr.isQualified()) {
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

  @Nullable
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
          return ((String)result).replace((String)arg1, (String)arg2);
        }
      }
    }

    // Support dict([("k": "v")]) syntax
    if (call.isCallee(PythonFQDNNames.DICT_CLASS)) {
      final Collection<PyTupleExpression> tuples = PsiTreeUtil.findChildrenOfType(call, PyTupleExpression.class);
      if (!tuples.isEmpty()) {
        final Map<Object, Object> result = new HashMap<>();
        for (final PyTupleExpression tuple : tuples) {
          final PsiElement[] tupleElements = tuple.getChildren();
          if (tupleElements.length != 2) {
            return null;
          }
          final PyExpression key = PyUtil.as(tupleElements[0], PyExpression.class);
          final PyExpression value = PyUtil.as(tupleElements[1], PyExpression.class);
          if ((key != null)) {
            addRecordFromDict(result, key, value);
          }
        }
        return result;
      }
    }


    return null;
  }

  /**
   * Adds record for map when working with dict
   *
   * @param result map to return to user
   * @param key    dict key
   * @param value  dict value
   */
  private void addRecordFromDict(@NotNull final Map<Object, Object> result,
                                 @NotNull final PyExpression key,
                                 @Nullable final PyExpression value) {
    result.put(myEvaluateKeys ? evaluate(key) : key, myEvaluateCollectionItems ? evaluate(value) : value);
  }

  /**
   * Shortcut that evaluates expression with default params and casts it to particular type (if possible)
   *
   * @param expression exp to evaluate
   * @param resultType expected type
   * @param <T>        expected type
   * @return value if expression is evaluated to this type, null otherwise
   */
  @Nullable
  public static <T> T evaluate(@Nullable final PyExpression expression, @NotNull final Class<T> resultType) {
    return PyUtil.as(new PyEvaluator().evaluate(expression), resultType);
  }
}
