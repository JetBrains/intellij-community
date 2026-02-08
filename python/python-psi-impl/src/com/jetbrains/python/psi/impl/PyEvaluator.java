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
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFQDNNames;
import com.jetbrains.python.nameResolver.NameResolverTools;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyBoolLiteralExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyPrefixExpression;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TODO: Merge PythonDataflowUtil and {@link PyEvaluator} and all its inheritors and improve Abstract Interpretation
 */
public class PyEvaluator {

  private static final QualifiedName SYS_VERSION_INFO = QualifiedName.fromDottedString("sys.version_info");

  private static final List<QualifiedName> TYPING_TYPE_CHECKING_NAMES = List.of(
    QualifiedName.fromDottedString("TYPE_CHECKING"),
    QualifiedName.fromDottedString("typing.TYPE_CHECKING")
  );

  private final @NotNull Set<PyExpression> myVisited = new HashSet<>();

  private @Nullable Map<String, Object> myNamespace = null;

  private int @Nullable [] myPythonVersion;

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

  /**
   * Store expressions as values if they can't be evaludated
   */
  private boolean myAllowExpressionsAsValues = false;

  public void setNamespace(@Nullable Map<String, Object> namespace) {
    myNamespace = namespace;
  }

  public void setLanguageLevel(@NotNull LanguageLevel languageLevel) {
    myPythonVersion = new int[]{languageLevel.getMajorVersion(), languageLevel.getMinorVersion()};
  }

  public void setEvaluateCollectionItems(boolean evaluateCollectionItems) {
    myEvaluateCollectionItems = evaluateCollectionItems;
  }

  public void setAllowExpressionsAsValues(final boolean allowExpressionsAsValues) {
    myAllowExpressionsAsValues = allowExpressionsAsValues;
  }

  public void setEvaluateKeys(boolean evaluateKeys) {
    myEvaluateKeys = evaluateKeys;
  }

  public void enableResolve(boolean enableResolve) {
    myEnableResolve = enableResolve;
  }

  @Contract("null -> null")
  public @Nullable Object evaluate(@Nullable PyExpression expression) {
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
    final Boolean bool = getBooleanLiteralValue(expression);
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

  private static @Nullable Object evaluateNumeric(@NotNull PyNumericLiteralExpression expression) {
    if (expression.isIntegerLiteral()) {
      final BigInteger value = expression.getBigIntegerValue();
      return fromBigInteger(value);
    }

    return null;
  }

  protected @Nullable Object evaluateBinary(@NotNull PyBinaryExpression expression) {
    final PyElementType op = expression.getOperator();

    if (myPythonVersion != null && PyTokenTypes.RELATIONAL_OPERATIONS.contains(op)) {
      if (isSysVersionInfoExpression(expression.getLeftExpression())) {
        int[] version = evaluateAsVersion(expression.getRightExpression());
        if (version != null) {
          return evaluateVersionCheck(op, myPythonVersion, version);
        }
      }
      else if (isSysVersionInfoExpression(expression.getRightExpression())) {
        int[] version = evaluateAsVersion(expression.getLeftExpression());
        if (version != null) {
          return evaluateVersionCheck(op, version, myPythonVersion);
        }
      }
    }

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
    else if (lhs instanceof Boolean first && rhs instanceof Boolean second) {
      if (op == PyTokenTypes.AND_KEYWORD) {
        return first && second;
      }
      else if (op == PyTokenTypes.OR_KEYWORD) {
        return first || second;
      }
    }

    return null;
  }

  @ApiStatus.Internal
  public static int @Nullable [] evaluateAsVersion(@Nullable PyExpression expression) {
    if (!(PyPsiUtils.flattenParens(expression) instanceof PyTupleExpression tupleExpression)) {
      return null;
    }
    PyExpression[] elements = tupleExpression.getElements();
    int[] result = new int[elements.length];
    for (int i = 0; i < elements.length; i++) {
      Integer num = evaluate(elements[i], Integer.class);
      if (num == null) {
        return null;
      }
      result[i] = num;
    }
    return result;
  }

  private static boolean evaluateVersionCheck(@NotNull PyElementType op, int @NotNull [] lhs, int @NotNull [] rhs) {
    if (op == PyTokenTypes.LT) {
      return ArrayUtil.lexicographicCompare(lhs, rhs) < 0;
    }
    if (op == PyTokenTypes.LE) {
      return ArrayUtil.lexicographicCompare(lhs, rhs) <= 0;
    }
    if (op == PyTokenTypes.GT) {
      return ArrayUtil.lexicographicCompare(lhs, rhs) > 0;
    }
    if (op == PyTokenTypes.GE) {
      return ArrayUtil.lexicographicCompare(lhs, rhs) >= 0;
    }
    throw new IllegalArgumentException();
  }

  @ApiStatus.Internal
  public static @Nullable Boolean getBooleanLiteralValue(@NotNull PsiElement expression) {
    if (expression instanceof PyBoolLiteralExpression) {
      if (PyNames.DEBUG.equals(expression.getText())) {
        return null;
      }
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

  private @Nullable Object evaluatePrefix(@NotNull PyPrefixExpression expression) {
    if (expression.getOperator() == PyTokenTypes.NOT_KEYWORD) {
      final Boolean value = PyUtil.as(evaluate(expression.getOperand()), Boolean.class);
      if (value != null) {
        return !value;
      }
    }
    else if (expression.getOperator() == PyTokenTypes.MINUS) {
      final Number number = PyUtil.as(evaluate(expression.getOperand()), Number.class);
      if (number != null) {
        return fromBigInteger(toBigInteger(number).negate());
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
  protected @NotNull Object evaluateSequence(@NotNull PySequenceExpression expression) {
    if (expression instanceof PyDictLiteralExpression) {
      final Map<Object, Object> result = new HashMap<>();
      for (final PyKeyValueExpression keyValue : ((PyDictLiteralExpression)expression).getElements()) {
        addRecordFromDict(result, keyValue.getKey(), keyValue.getValue());
      }
      return result;
    }

    return ContainerUtil.map(expression.getElements(), element -> myEvaluateCollectionItems ? evaluate(element) : element);
  }

  public @Nullable Object applyPlus(@Nullable Object lhs, @Nullable Object rhs) {
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

  protected @Nullable Object evaluateReference(@NotNull PyReferenceExpression expression) {
    if (isTypeCheckingExpression(expression)) {
      return true;
    }
    if (!expression.isQualified()) {
      if (myNamespace != null) {
        return myNamespace.get(expression.getReferencedName());
      }
      if (!myEnableResolve) {
        return null;
      }
      final var context = TypeEvalContext.codeInsightFallback(expression.getProject());
      final ResolveResult[] results = expression.getReference(PyResolveContext.defaultContext(context)).multiResolve(false);
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

  protected @Nullable Object evaluateCall(@NotNull PyCallExpression expression) {
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
    if (myEnableResolve && NameResolverTools.isCalleeShortCut(expression, PythonFQDNNames.DICT_CLASS)) {
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

      final PyExpression[] arguments = expression.getArguments();
      //dict(foo="spam")
      if (arguments.length > 0) {
        final Map<Object, Object> result = new HashMap<>();
        for (final PyExpression argument : arguments) {
          if (!(argument instanceof PyKeywordArgument keywordArgument)) {
            continue;
          }
          final String keyword = keywordArgument.getKeyword();
          if (keyword == null) {
            continue;
          }
          addRecordFromDict(result, keyword, keywordArgument.getValueExpression());
        }
        return result;
      }
    }
    return null;
  }

  private void addRecordFromDict(@NotNull Map<Object, Object> result, @NotNull PyExpression key, @Nullable PyExpression value) {
    result.put(myEvaluateKeys ? evaluate(key) : key, myEvaluateCollectionItems ? evaluateOrGet(value) : value);
  }

  private void addRecordFromDict(@NotNull Map<Object, Object> result, @NotNull String key, @Nullable PyExpression value) {
    result.put(key, myEvaluateCollectionItems ? evaluateOrGet(value) : value);
  }

  private static @NotNull BigInteger toBigInteger(@NotNull Number value) {
    // don't forget to update fromBigInteger() after changing this method

    return value instanceof Integer
           ? BigInteger.valueOf((Integer)value)
           : value instanceof Long
             ? BigInteger.valueOf((Long)value)
             : (BigInteger)value;
  }

  private static @NotNull Number fromBigInteger(@NotNull BigInteger value) {
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
  public static @Nullable <T> T evaluate(@Nullable PyExpression expression, @NotNull Class<T> resultType) {
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
  public static @Nullable <T> T evaluateNoResolve(@Nullable PyExpression expression, @NotNull Class<T> resultType) {
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
  public static @Nullable Boolean evaluateAsBoolean(@Nullable PyExpression expression) {
    return evaluateAsBoolean(prepareEvaluatorForBoolean(true), expression);
  }

  /**
   * Shortcut that evaluates expression and tries to determine if `bool` will return true for it
   *
   * @param expression expression to evaluate
   * @return true if expression is evaluated to value so `bool` returns true for it
   */
  public static @Nullable Boolean evaluateAsBooleanNoResolve(@Nullable PyExpression expression) {
    return evaluateAsBoolean(prepareEvaluatorForBoolean(false), expression);
  }

  @ApiStatus.Experimental
  public static Boolean evaluateAsBooleanNoResolve(@Nullable PyExpression expression, @Nullable LanguageLevel languageLevel) {
    PyEvaluator evaluator = prepareEvaluatorForBoolean(false);
    if (languageLevel != null) {
      evaluator.setLanguageLevel(languageLevel);
    }
    return evaluateAsBoolean(evaluator, expression);
  }

  public static boolean evaluateAsBoolean(@Nullable PyExpression expression, boolean defaultValue) {
    return ObjectUtils.notNull(evaluateAsBoolean(expression), defaultValue);
  }

  public static boolean evaluateAsBooleanNoResolve(@Nullable PyExpression expression, boolean defaultValue) {
    return ObjectUtils.notNull(evaluateAsBooleanNoResolve(expression), defaultValue);
  }

  private static @NotNull PyEvaluator prepareEvaluatorForBoolean(boolean enableResolve) {
    final PyEvaluator evaluator = new PyEvaluator();
    evaluator.setEvaluateCollectionItems(false);
    evaluator.setEvaluateKeys(false);
    evaluator.enableResolve(enableResolve);
    return evaluator;
  }

  private static @Nullable Boolean evaluateAsBoolean(@NotNull PyEvaluator evaluator, @Nullable PyExpression expression) {
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
      return !((Collection<?>)result).isEmpty();
    }
    else if (result instanceof Map) {
      return !((Map<?, ?>)result).isEmpty();
    }

    return null;
  }

  private @Nullable Object evaluateOrGet(final @Nullable PyExpression expression) {
    final Object result = evaluate(expression);
    if (result != null) {
      return result;
    }
    return myAllowExpressionsAsValues ? expression : null;
  }

  @ApiStatus.Internal
  public static boolean isSysVersionInfoExpression(@Nullable PyExpression expression) {
    return PyPsiUtils.flattenParens(expression) instanceof PyReferenceExpression referenceExpression &&
           SYS_VERSION_INFO.equals(referenceExpression.asQualifiedName());
  }

  @ApiStatus.Internal
  public static boolean isTypeCheckingExpression(@Nullable PyExpression expression) {
    return expression instanceof PyReferenceExpression referenceExpression && isTypeCheckingExpression(referenceExpression);
  }

  private static boolean isTypeCheckingExpression(@NotNull PyReferenceExpression expression) {
    QualifiedName qualifiedName = expression.asQualifiedName();
    return qualifiedName != null && TYPING_TYPE_CHECKING_NAMES.contains(qualifiedName);
  }
}
