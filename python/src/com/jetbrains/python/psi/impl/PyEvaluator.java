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

import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFQDNNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.*;

/**
 * TODO: Merge PythonDataflowUtil and {@link PyEvaluator} and all its inheritors and improve Abstract Interpretation
 *
 * @author yole
 */
public class PyEvaluator {

  @NotNull
  private final Set<PyExpression> myVisited = new HashSet<>();

  @Nullable
  private Map<String, Object> myNamespace = null;

  /**
   * if true, collection items or dict values will be evaluated
   */
  private boolean myEvaluateCollectionItems = true;

  /**
   * if true, dict keys will be evaluated
   */
  private boolean myEvaluateKeys = true;

  /**
   * if true, references will be resolved
   */
  private boolean myEnableResolve = true;

  public void setNamespace(@Nullable Map<String, Object> namespace) {
    myNamespace = namespace;
  }

  public void setEvaluateCollectionItems(boolean evaluateCollectionItems) {
    myEvaluateCollectionItems = evaluateCollectionItems;
  }

  public void setEvaluateKeys(boolean evaluateKeys) {
    myEvaluateKeys = evaluateKeys;
  }

  public void enableResolve(boolean enableResolve) {
    myEnableResolve = enableResolve;
  }

  @Nullable
  @Contract("null -> null")
  public Object evaluate(@Nullable PyExpression expression) {
    if (expression == null || myVisited.contains(expression)) {
      return null;
    }
    PyUtil.verboseOnly(() -> PyPsiUtils.assertValid(expression));
    myVisited.add(expression);
    if (expression instanceof PyParenthesizedExpression) {
      return evaluate(((PyParenthesizedExpression)expression).getContainedExpression());
    }
    else if (expression instanceof PySequenceExpression) {
      return evaluateSequence((PySequenceExpression)expression);
    }
    final Boolean bool = evaluateBoolean(expression);
    if (bool != null) {
      return bool;
    }
    if (expression instanceof PyCallExpression) {
      return evaluateCall((PyCallExpression)expression);
    }
    else if (expression instanceof PyReferenceExpression) {
      return evaluateReference((PyReferenceExpression)expression);
    }
    else if (expression instanceof PyStringLiteralExpression) {
      return ((PyStringLiteralExpression)expression).getStringValue();
    }
    else if (expression instanceof PyBinaryExpression) {
      return evaluateBinary((PyBinaryExpression)expression);
    }
    else if (expression instanceof PyNumericLiteralExpression) {
      return evaluateNumeric((PyNumericLiteralExpression)expression);
    }
    else if (expression instanceof PyPrefixExpression) {
      return evaluatePrefix((PyPrefixExpression)expression);
    }
    return null;
  }

  @Nullable
  private static Object evaluateNumeric(@NotNull PyNumericLiteralExpression expression) {
    if (expression.isIntegerLiteral()) {
      final BigInteger value = expression.getBigIntegerValue();
      if (value != null) {
        return fromBigInteger(value);
      }
    }

    return null;
  }

  @Nullable
  private Object evaluateBinary(@NotNull PyBinaryExpression expression) {
    final PyElementType op = expression.getOperator();
    final Object lhs = evaluate(expression.getLeftExpression());
    final Object rhs = evaluate(expression.getRightExpression());
    if (op == PyTokenTypes.PLUS && lhs != null && rhs != null) {
      return applyPlus(lhs, rhs);
    }
    else if (lhs instanceof Number && rhs instanceof Number) {
      final BigInteger first = toBigInteger((Number)lhs);
      final BigInteger second = toBigInteger((Number)rhs);
      if (op == PyTokenTypes.MINUS) {
        return fromBigInteger(first.subtract(second));
      }
      else if (op == PyTokenTypes.MULT) {
        return fromBigInteger(first.multiply(second));
      }
      else if (op == PyTokenTypes.FLOORDIV) {
        return fromBigInteger(first.divide(second));
      }
      else if (op == PyTokenTypes.LT) {
        return first.compareTo(second) < 0;
      }
      else if (op == PyTokenTypes.LE) {
        return first.compareTo(second) <= 0;
      }
      else if (op == PyTokenTypes.GT) {
        return first.compareTo(second) > 0;
      }
      else if (op == PyTokenTypes.GE) {
        return first.compareTo(second) >= 0;
      }
      else if (op == PyTokenTypes.EQEQ) {
        return first.compareTo(second) == 0;
      }
      else if (op == PyTokenTypes.NE) {
        return first.compareTo(second) != 0;
      }
    }
    else if (lhs instanceof Boolean && rhs instanceof Boolean) {
      final Boolean first = (Boolean)lhs;
      final Boolean second = (Boolean)rhs;
      if (op == PyTokenTypes.AND_KEYWORD) {
        return first && second;
      }
      else if (op == PyTokenTypes.OR_KEYWORD) {
        return first || second;
      }
    }

    return null;
  }

  @Nullable
  private static Boolean evaluateBoolean(@NotNull PyExpression expression) {
    if (expression instanceof PyBoolLiteralExpression) {
      return ((PyBoolLiteralExpression)expression).getValue();
    }
    else if (expression instanceof PyReferenceExpression && LanguageLevel.forElement(expression).isPython2()) {
      final String text = ((PyQualifiedExpression)expression).getReferencedName();
      if (PyNames.TRUE.equals(text)) {
        return true;
      }
      if (PyNames.FALSE.equals(text)) {
        return false;
      }
    }

    return null;
  }

  @Nullable
  private Object evaluatePrefix(@NotNull PyPrefixExpression expression) {
    if (expression.getOperator() == PyTokenTypes.NOT_KEYWORD) {
      final Boolean value = PyUtil.as(evaluate(expression.getOperand()), Boolean.class);
      if (value != null) {
        return !value;
      }
    }

    return null;
  }

  /**
   * Evaluates sequence (tuple, list, set, dict)
   *
   * @param expression sequence expression
   * @return evaluated sequence
   */
  @NotNull
  protected Object evaluateSequence(@NotNull PySequenceExpression expression) {
    if (expression instanceof PyDictLiteralExpression) {
      final Map<Object, Object> result = new HashMap<>();
      for (final PyKeyValueExpression keyValue : ((PyDictLiteralExpression)expression).getElements()) {
        addRecordFromDict(result, keyValue.getKey(), keyValue.getValue());
      }
      return result;
    }

    return ContainerUtil.map(expression.getElements(), element -> myEvaluateCollectionItems ? evaluate(element) : element);
  }

  @Nullable
  public Object applyPlus(@Nullable Object lhs, @Nullable Object rhs) {
    if (lhs instanceof String && rhs instanceof String) {
      return (String)lhs + rhs;
    }
    else if (lhs instanceof List && rhs instanceof List) {
      return ContainerUtil.concat((List)lhs, (List)rhs);
    }
    else if (lhs instanceof Number && rhs instanceof Number) {
      final BigInteger first = toBigInteger((Number)lhs);
      final BigInteger second = toBigInteger((Number)rhs);
      return fromBigInteger(first.add(second));
    }
    return null;
  }

  @Nullable
  protected Object evaluateReference(@NotNull PyReferenceExpression expression) {
    if (!expression.isQualified()) {
      if (myNamespace != null) {
        return myNamespace.get(expression.getReferencedName());
      }
      if (!myEnableResolve) {
        return null;
      }
      final ResolveResult[] results = expression.getReference(PyResolveContext.noImplicits()).multiResolve(false);
      if (results.length != 1) {
        return null;
      }
      PsiElement result = results[0].getElement();
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
  protected Object evaluateCall(@NotNull PyCallExpression expression) {
    final PyExpression[] args = expression.getArguments();
    if (expression.isCalleeText(PyNames.REPLACE) && args.length == 2) {
      final PyExpression callee = expression.getCallee();
      if (callee instanceof PyQualifiedExpression) {
        final Object evaluatedQualifier = evaluate(((PyQualifiedExpression)callee).getQualifier());
        if (evaluatedQualifier instanceof String) {
          final Object oldSubstring = evaluate(args[0]);
          final Object newSubstring = evaluate(args[1]);
          if (oldSubstring instanceof String && newSubstring instanceof String) {
            return ((String)evaluatedQualifier).replace((String)oldSubstring, (String)newSubstring);
          }
        }
      }
    }

    // Support dict([("k", "v")]) syntax
    if (myEnableResolve && expression.isCallee(PythonFQDNNames.DICT_CLASS)) {
      final Collection<PyTupleExpression> tuples = PsiTreeUtil.findChildrenOfType(expression, PyTupleExpression.class);
      if (!tuples.isEmpty()) {
        final Map<Object, Object> result = new HashMap<>();
        for (final PyTupleExpression tuple : tuples) {
          final PsiElement[] tupleElements = tuple.getChildren();
          if (tupleElements.length != 2) {
            return null;
          }
          final PyExpression key = PyUtil.as(tupleElements[0], PyExpression.class);
          final PyExpression value = PyUtil.as(tupleElements[1], PyExpression.class);
          if (key != null) {
            addRecordFromDict(result, key, value);
          }
        }
        return result;
      }
    }

    return null;
  }

  private void addRecordFromDict(@NotNull Map<Object, Object> result, @NotNull PyExpression key, @Nullable PyExpression value) {
    result.put(myEvaluateKeys ? evaluate(key) : key, myEvaluateCollectionItems ? evaluate(value) : value);
  }

  @NotNull
  private static BigInteger toBigInteger(@NotNull Number value) {
    // don't forget to update fromBigInteger() after changing this method

    return value instanceof Integer
           ? BigInteger.valueOf((Integer)value)
           : value instanceof Long
             ? BigInteger.valueOf((Long)value)
             : (BigInteger)value;
  }

  @NotNull
  private static Number fromBigInteger(@NotNull BigInteger value) {
    // don't forget to update toBigInteger() after changing this method

    final int intValue = value.intValue();
    if (BigInteger.valueOf(intValue).equals(value)) {
      return intValue;
    }

    final long longValue = value.longValue();
    if (BigInteger.valueOf(longValue).equals(value)) {
      return longValue;
    }

    return value;
  }

  /**
   * Shortcut that evaluates expression with default params and casts it to particular type (if possible)
   *
   * @param <T>        expected type
   * @param expression expression to evaluate
   * @param resultType expected type
   * @return value if expression is evaluated to this type, null otherwise
   */
  @Nullable
  public static <T> T evaluate(@Nullable PyExpression expression, @NotNull Class<T> resultType) {
    return PyUtil.as(new PyEvaluator().evaluate(expression), resultType);
  }

  /**
   * Shortcut that evaluates expression with default params and disabled resolve and casts it to particular type (if possible)
   *
   * @param <T>        expected type
   * @param expression expression to evaluate
   * @param resultType expected type
   * @return value if expression is evaluated to this type, null otherwise
   */
  @Nullable
  public static <T> T evaluateNoResolve(@Nullable PyExpression expression, @NotNull Class<T> resultType) {
    final PyEvaluator evaluator = new PyEvaluator();
    evaluator.enableResolve(false);
    return PyUtil.as(evaluator.evaluate(expression), resultType);
  }

  /**
   * Shortcut that evaluates expression and tries to determine if `bool` will return true for it
   *
   * @param expression expression to evaluate
   * @return true if expression is evaluated to value so `bool` returns true for it
   */
  @Nullable
  public static Boolean evaluateAsBoolean(@Nullable PyExpression expression) {
    return evaluateAsBoolean(prepareEvaluatorForBoolean(true), expression);
  }

  /**
   * Shortcut that evaluates expression and tries to determine if `bool` will return true for it
   *
   * @param expression expression to evaluate
   * @return true if expression is evaluated to value so `bool` returns true for it
   */
  @Nullable
  public static Boolean evaluateAsBooleanNoResolve(@Nullable PyExpression expression) {
    return evaluateAsBoolean(prepareEvaluatorForBoolean(false), expression);
  }

  public static boolean evaluateAsBoolean(@Nullable PyExpression expression, boolean defaultValue) {
    return ObjectUtils.notNull(evaluateAsBoolean(expression), defaultValue);
  }

  public static boolean evaluateAsBooleanNoResolve(@Nullable PyExpression expression, boolean defaultValue) {
    return ObjectUtils.notNull(evaluateAsBooleanNoResolve(expression), defaultValue);
  }

  @NotNull
  private static PyEvaluator prepareEvaluatorForBoolean(boolean enableResolve) {
    final PyEvaluator evaluator = new PyEvaluator();
    evaluator.setEvaluateCollectionItems(false);
    evaluator.setEvaluateKeys(false);
    evaluator.enableResolve(enableResolve);
    return evaluator;
  }

  @Nullable
  private static Boolean evaluateAsBoolean(@NotNull PyEvaluator evaluator, @Nullable PyExpression expression) {
    final Object result = evaluator.evaluate(expression);

    if (result instanceof Boolean) {
      return (Boolean)result;
    }
    else if (result instanceof Integer) {
      return (Integer)result != 0;
    }
    else if (result instanceof String) {
      return !((String)result).isEmpty();
    }
    else if (result instanceof Collection) {
      return !((Collection)result).isEmpty();
    }
    else if (result instanceof Map) {
      return !((Map)result).isEmpty();
    }

    return null;
  }
}
