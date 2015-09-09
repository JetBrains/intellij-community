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
package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.FunctionParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Represents an argument list of a function call.
 *
 * @author yole
 */
public interface PyArgumentList extends PyElement {

  /**
   * @return all argument list param expressions (keyword argument or nameless)
   */
  @NotNull
  Collection<PyExpression> getArgumentExpressions();

  @NotNull
  PyExpression[] getArguments();

  @Nullable
  PyKeywordArgument getKeywordArgument(String name);

  /**
   * TODO: Copy/Paste with {@link com.jetbrains.python.psi.PyCallExpression#addArgument(PyExpression)} ?
   * Adds argument to the appropriate place:
   * {@link com.jetbrains.python.psi.PyKeywordArgument} goes to the end.
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
  @Nullable
  PyCallExpression getCallExpression();


  @Nullable
  ASTNode getClosingParen();

  /**
   * Searches parameter value and returns it if exists.
   *
   * @param parameter param to search
   * @return function parameter value expression or null if does not exist
   */
  @Nullable
  PyExpression getValueExpressionForParam(@NotNull FunctionParameter parameter);
}
