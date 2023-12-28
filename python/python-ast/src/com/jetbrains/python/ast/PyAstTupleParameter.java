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
package com.jetbrains.python.ast;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.ast.impl.ParamHelperCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tuple parameter. Defines nothing; this interface is only needed for stub creation.
 */
@ApiStatus.Experimental
public interface PyAstTupleParameter extends PyAstParameter {

  @Override
  @Nullable
  default PyAstNamedParameter getAsNamed() {
    return null;  // we're not named
  }

  @Override
  @NotNull
  default PyAstTupleParameter getAsTuple() {
    return this;
  }

  @Override
  @Nullable
  default PyAstExpression getDefaultValue() {
    ASTNode[] nodes = getNode().getChildren(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens());
    if (nodes.length > 0) {
      return (PyAstExpression)nodes[0].getPsi();
    }
    return null;
  }

  @Override
  default boolean hasDefaultValue() {
    return getDefaultValue() != null;
  }

  @Override
  @Nullable
  default String getDefaultValueText() {
    return ParamHelperCore.getDefaultValueText(getDefaultValue());
  }

  /**
   * @return the nested parameters within this tuple parameter.
   */
  PyAstParameter @NotNull [] getContents();
}
