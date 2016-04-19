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

import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Evaluator tht supports expression of any type (it evaluates any expression, concatenating several expressions to list etc)
 */
public class PyAnyExpressionEvaluator extends PyEvaluator {
  private final boolean myEvalSequence;

  /**
   * @param evalSequence evaluate sequencies ((a,b) + (c,d) == (a,b,c,d)) or store them as single ((a,b) + (c,d) = [(a,b), (c,d)])
   */
  public PyAnyExpressionEvaluator(final boolean evalSequence) {
    myEvalSequence = evalSequence;
  }

  @Nullable
  @Override
  public Object evaluate(final PyExpression expr) {
    final Object evaluate = super.evaluate(expr);
    return ((evaluate != null) ? evaluate : expr);
  }

  @Override
  public Object concatenate(final Object lhs, final Object rhs) {
    final Object evaluate = super.concatenate(lhs, rhs);
    return ((evaluate != null) ? evaluate : Arrays.asList(lhs, rhs));
  }

  @Override
  protected Object evaluateReferenceExpression(final PyReferenceExpression expr) {
    final Object evaluate = super.evaluateReferenceExpression(expr);
    return ((evaluate != null) ? evaluate : expr);
  }

  @Override
  protected Object evaluateCall(final PyCallExpression call) {
    final Object evaluate = super.evaluateCall(call);
    return ((evaluate != null) ? evaluate : call);
  }

  @Override
  protected Object evaluateSequenceExpression(final PySequenceExpression expr) {
    return myEvalSequence ? super.evaluateSequenceExpression(expr) : expr;
  }

  /**
   * Evaluates expression to single element
   * @param expression exp to eval
   * @param aClass expected class
   * @param <T>    expected class
   * @return instance of aClass, or null if failed to eval
   */
  @Nullable
  public static <T> T evaluateOne(@NotNull final PyExpression expression, @NotNull final Class<T> aClass) {
    final PyAnyExpressionEvaluator evaluator = new PyAnyExpressionEvaluator(false);
    final Object evaluate = evaluator.evaluate(expression);
    final T resultSingle = PyUtil.as(evaluate, aClass);
    if (resultSingle != null) {
      return resultSingle;
    }
    final List<?> resultMultiple = PyUtil.as(evaluate, List.class);
    if ((resultMultiple != null) && !resultMultiple.isEmpty()) {
      return PyUtil.as(resultMultiple.get(0), aClass);
    }
    return null;
  }


  /**
   * Evaluates expression to string
   * @param expression exp to eval
   * @return string, or null if failed to eval
   */
  @Nullable
  public static String evaluateString(@NotNull final PyExpression expression) {
    return PyUtil.as(new PyAnyExpressionEvaluator(false).evaluate(expression), String.class);
  }

  /**
   * Evaluates expression as list of values
   * @param expression exp to eval
   * @param aClass expected element class
   * @param <T> expected element class
   * @return a list of elements of expected type
   */
  @NotNull
  public static <T>List<T> evaluateIterable(@NotNull final PyExpression expression, @NotNull final Class<T> aClass) {
    final PyAnyExpressionEvaluator evaluator = new PyAnyExpressionEvaluator(true);
    final Object evaluate = evaluator.evaluate(expression);
    final T resultSingle = PyUtil.as(evaluate, aClass);
    if (resultSingle != null) {
      return Collections.singletonList(resultSingle);
    }
    return PyUtil.asList(PyUtil.as(evaluate, List.class), aClass);
  }
}
