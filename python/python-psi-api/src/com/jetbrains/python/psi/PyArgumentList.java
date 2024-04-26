// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.jetbrains.python.FunctionParameter;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.ast.PyAstArgumentList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Represents an argument list of a function call.
 */
public interface PyArgumentList extends PyAstArgumentList, PyElement {

  /**
   * @return all argument list param expressions (keyword argument or nameless)
   */
  @Override
  @NotNull
  default Collection<PyExpression> getArgumentExpressions() {
    //noinspection unchecked
    return (Collection<PyExpression>)PyAstArgumentList.super.getArgumentExpressions();
  }

  @Override
  default PyExpression @NotNull [] getArguments() {
    return childrenToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), PyExpression.EMPTY_ARRAY);
  }

  @Override
  @Nullable
  default PyKeywordArgument getKeywordArgument(String name) {
    return (PyKeywordArgument)PyAstArgumentList.super.getKeywordArgument(name);
  }

  /**
   * Adds argument to the appropriate place:
   * {@link PyKeywordArgument} goes to the end.
   * All other go before key arguments (if any) but after last non-key arguments.
   * Commas should be set correctly as well.
   *
   * @param arg argument to add
   */
  void addArgument(@NotNull PyExpression arg);

  void addArgumentFirst(PyExpression arg);

  void addArgumentAfter(PyExpression argument, PyExpression afterThis);

  /**
   * @return the call expression to which this argument list belongs; not null in correctly parsed cases.
   */
  @Override
  @Nullable
  default PyCallExpression getCallExpression() {
    return (PyCallExpression)PyAstArgumentList.super.getCallExpression();
  }

  /**
   * Searches parameter value and returns it if exists.
   *
   * @param parameter param to search
   * @return function parameter value expression or null if does not exist
   */
  @Nullable
  PyExpression getValueExpressionForParam(@NotNull FunctionParameter parameter);
}
