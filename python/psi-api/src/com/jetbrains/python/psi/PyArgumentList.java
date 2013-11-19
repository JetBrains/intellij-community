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
package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an argument list of a function call.
 *
 * @author yole
 */
public interface PyArgumentList extends PyElement {

  @NotNull PyExpression[] getArguments();

  @Nullable PyKeywordArgument getKeywordArgument(String name);

  void addArgument(PyExpression arg);
  void addArgumentFirst(PyExpression arg);
  void addArgumentAfter(PyExpression argument, PyExpression afterThis);

  /**
   * @return the call expression to which this argument list belongs; not null in correctly parsed cases.
   */
  @Nullable
  PyCallExpression getCallExpression();

  /**
   * Tries to map the argument list to callee's idea of parameters.
   * @return a result object with mappings and diagnostic flags.
   * @param resolveContext the reference resolution context
   * @param implicitOffset known from the context implicit offset
   */
  @NotNull
  CallArgumentsMapping analyzeCall(PyResolveContext resolveContext, int implicitOffset);

  @NotNull
  CallArgumentsMapping analyzeCall(PyResolveContext resolveContext);


  @Nullable
  ASTNode getClosingParen();
}
